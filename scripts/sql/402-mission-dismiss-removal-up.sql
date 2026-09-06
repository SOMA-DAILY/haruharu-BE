BEGIN;

-- #402: 미션 카드 dismiss 제거. 카드를 "닫는" 것은 FE 로컬 동작이라 서버 해소 상태가 아니다.
-- 해소(resolution)는 시간창 안의 기대 채널 활동 또는 시간창 마감(EXPIRED)으로만 일어난다.

-- (1) 기존 DISMISSED 카드를 새 정책으로 재해석한다. 단순 NULL 초기화는 안 된다 — 이미 닫혀 outcome 이 확정된 미션은
--     확정 배치가 다시 훑지 않으므로(findClosedWithoutOutcome 제외) 영원히 미해소로 남는다. 따라서 여기서 직접 판정한다.
--       a. 최초 노출 이후 기대 채널 활동이 있었으면 → 활동 해소 (recordActivity 와 동일 규칙: last_expected_activity_at >= first_impressed_at)
--       b. 그 외, 시간창이 이미 닫혔으면          → EXPIRED (resolved_at = closes_at, 확정 배치가 부여했을 값과 동일)
--       c. 그 외(아직 열린 창)                    → NULL/NULL — 재노출되고 이후 활동·마감이 정상 처리한다
--     resolution_pair CHECK 가 (resolution, resolved_at) 동시 NULL/NOT NULL 을 요구하므로 두 컬럼을 같은 CASE 로 맞춘다.
--     이미 확정된 mission_outcomes 행은 불변 스냅샷(과거 정책 기준선)이라 재집계하지 않는다.
--     closes_at 은 앱이 서울 벽시계(LocalDateTime)로 저장한 timestamp without time zone 이다. RDS 세션은 UTC 라
--     맨 CURRENT_TIMESTAMP 와 비교하면 9시간 어긋나므로 반드시 'Asia/Seoul' 벽시계로 변환해 비교한다.
WITH closing AS (
    SELECT mission.id AS room_mission_id,
           COALESCE(mission.closes_at, slot.closes_at) AS closes_at
    FROM room_missions mission
    LEFT JOIN room_mission_slots slot ON slot.id = mission.room_mission_slot_id
)
UPDATE mission_member_states state
SET resolution = CASE
        WHEN state.first_impressed_at IS NOT NULL
         AND state.last_expected_activity_at IS NOT NULL
         AND state.last_expected_activity_at >= state.first_impressed_at
            THEN CASE state.expected_channel
                     WHEN 'VIDEO' THEN 'VIDEO_UPLOAD_AFTER_VIEW'
                     WHEN 'GROUP_CHAT' THEN 'CHAT_MESSAGE_AFTER_VIEW'
                 END
        WHEN closing.closes_at IS NOT NULL AND closing.closes_at <= (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
            THEN 'EXPIRED'
        ELSE NULL
    END,
    resolved_at = CASE
        WHEN state.first_impressed_at IS NOT NULL
         AND state.last_expected_activity_at IS NOT NULL
         AND state.last_expected_activity_at >= state.first_impressed_at
            THEN CASE
                     WHEN state.first_expected_activity_at >= state.first_impressed_at
                         THEN state.first_expected_activity_at
                     ELSE state.last_expected_activity_at
                 END
        WHEN closing.closes_at IS NOT NULL AND closing.closes_at <= (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
            THEN closing.closes_at
        ELSE NULL
    END,
    updated_at = CURRENT_TIMESTAMP
FROM closing
WHERE closing.room_mission_id = state.room_mission_id
  AND state.resolution = 'DISMISSED';

-- (2) 허용 목록에서 DISMISSED 제거.
ALTER TABLE mission_member_states
    DROP CONSTRAINT ck_mission_member_states_resolution;

ALTER TABLE mission_member_states
    ADD CONSTRAINT ck_mission_member_states_resolution
        CHECK (resolution IS NULL OR resolution IN (
            'VIDEO_UPLOAD_AFTER_VIEW', 'CHAT_MESSAGE_AFTER_VIEW', 'EXPIRED'));

-- (3) outcome 스냅샷에서 dismissed_member_count 제거. 컬럼을 참조하는 CHECK 를 먼저 재정의한다.
ALTER TABLE mission_outcomes
    DROP CONSTRAINT ck_mission_outcomes_counts_non_negative;

ALTER TABLE mission_outcomes
    DROP CONSTRAINT ck_mission_outcomes_counts_within_target;

ALTER TABLE mission_outcomes
    DROP COLUMN dismissed_member_count;

ALTER TABLE mission_outcomes
    ADD CONSTRAINT ck_mission_outcomes_counts_non_negative
        CHECK (target_member_count >= 0
            AND impressed_member_count >= 0
            AND activated_member_count >= 0
            AND expected_channel_activated_count >= 0
            AND cross_channel_activated_count >= 0
            AND expired_member_count >= 0);

ALTER TABLE mission_outcomes
    ADD CONSTRAINT ck_mission_outcomes_counts_within_target
        CHECK (impressed_member_count <= target_member_count
            AND activated_member_count <= target_member_count
            AND expected_channel_activated_count <= target_member_count
            AND cross_channel_activated_count <= target_member_count
            AND expired_member_count <= target_member_count);

COMMIT;

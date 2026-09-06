-- DISMISSED 가 허용 목록에서 빠졌는지 (VIDEO_UPLOAD_AFTER_VIEW · CHAT_MESSAGE_AFTER_VIEW · EXPIRED 만 남아야 함).
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conname = 'ck_mission_member_states_resolution';

-- DISMISSED 잔존 행(0 이어야 함).
SELECT COUNT(*) AS remaining_dismissed
FROM mission_member_states
WHERE resolution = 'DISMISSED';

-- resolution/resolved_at 쌍 불일치(0 이어야 함 — 백필이 두 컬럼을 같은 CASE 로 맞췄는지).
SELECT COUNT(*) AS resolution_pair_mismatch
FROM mission_member_states
WHERE (resolution IS NULL) <> (resolved_at IS NULL);

-- 닫힌 미션에 미해소 카드가 남았는지(0 이어야 함 — 닫힌 창의 DISMISSED 는 EXPIRED/활동으로 재판정돼야 한다).
SELECT COUNT(*) AS unresolved_in_closed_mission
FROM mission_member_states state
JOIN room_missions mission ON mission.id = state.room_mission_id
LEFT JOIN room_mission_slots slot ON slot.id = mission.room_mission_slot_id
WHERE state.resolution IS NULL
  AND state.deleted_at IS NULL
  AND COALESCE(mission.closes_at, slot.closes_at) <= (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul'); -- closes_at 은 서울 벽시계

-- dismissed_member_count 컬럼이 사라졌는지(0 행이어야 함).
SELECT column_name
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name = 'mission_outcomes'
  AND column_name = 'dismissed_member_count';

-- outcome CHECK 가 재정의됐는지(dismissed 참조가 없어야 함).
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conname IN ('ck_mission_outcomes_counts_non_negative', 'ck_mission_outcomes_counts_within_target');

BEGIN;

-- dismissed_member_count 복원. 되돌린 뒤 확정되는 행은 0 으로 채워진다(DEFAULT). 이미 확정된 스냅샷의 원래 값은 복구할 수 없다.
ALTER TABLE mission_outcomes
    DROP CONSTRAINT ck_mission_outcomes_counts_non_negative;

ALTER TABLE mission_outcomes
    DROP CONSTRAINT ck_mission_outcomes_counts_within_target;

ALTER TABLE mission_outcomes
    ADD COLUMN dismissed_member_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE mission_outcomes
    ADD CONSTRAINT ck_mission_outcomes_counts_non_negative
        CHECK (target_member_count >= 0
            AND impressed_member_count >= 0
            AND activated_member_count >= 0
            AND expected_channel_activated_count >= 0
            AND cross_channel_activated_count >= 0
            AND dismissed_member_count >= 0
            AND expired_member_count >= 0);

ALTER TABLE mission_outcomes
    ADD CONSTRAINT ck_mission_outcomes_counts_within_target
        CHECK (impressed_member_count <= target_member_count
            AND activated_member_count <= target_member_count
            AND expected_channel_activated_count <= target_member_count
            AND cross_channel_activated_count <= target_member_count
            AND dismissed_member_count <= target_member_count
            AND expired_member_count <= target_member_count);

-- 허용 목록에 DISMISSED 복원(371 시점 정의).
ALTER TABLE mission_member_states
    DROP CONSTRAINT ck_mission_member_states_resolution;

ALTER TABLE mission_member_states
    ADD CONSTRAINT ck_mission_member_states_resolution
        CHECK (resolution IS NULL OR resolution IN (
            'DISMISSED', 'VIDEO_UPLOAD_AFTER_VIEW', 'CHAT_MESSAGE_AFTER_VIEW', 'EXPIRED'));

COMMIT;

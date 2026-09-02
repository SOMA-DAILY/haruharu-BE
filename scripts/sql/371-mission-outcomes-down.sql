BEGIN;

DROP INDEX IF EXISTS idx_mission_outcomes_closes_at;

DROP TABLE IF EXISTS mission_outcomes;

-- EXPIRED 를 되돌리기 전에 이미 부여된 값을 비운다. 되돌린 CHECK 가 EXPIRED 를 허용하지 않기 때문이다.
UPDATE mission_member_states
SET resolution = NULL,
    resolved_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE resolution = 'EXPIRED';

ALTER TABLE mission_member_states
    DROP CONSTRAINT ck_mission_member_states_resolution;

ALTER TABLE mission_member_states
    ADD CONSTRAINT ck_mission_member_states_resolution
        CHECK (resolution IS NULL OR resolution IN (
            'DISMISSED', 'VIDEO_UPLOAD_AFTER_VIEW', 'CHAT_MESSAGE_AFTER_VIEW'));

COMMIT;

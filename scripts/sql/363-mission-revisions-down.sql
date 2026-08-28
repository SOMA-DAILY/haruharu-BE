BEGIN;

-- 롤백 역시 #363에서 합의한 데이터 초기화 정책을 따른다.
TRUNCATE TABLE room_missions, missions RESTART IDENTITY CASCADE;

ALTER TABLE room_missions
    DROP CONSTRAINT IF EXISTS fk_room_missions_revision,
    DROP COLUMN mission_revision_id,
    ADD COLUMN mission_id BIGINT NOT NULL;

ALTER TABLE missions
    DROP CONSTRAINT IF EXISTS fk_missions_active_revision;

DROP TABLE IF EXISTS mission_revision_eligibilities;
DROP TABLE IF EXISTS mission_revisions;

ALTER TABLE missions
    DROP COLUMN active_revision_id,
    DROP COLUMN active,
    ADD COLUMN description VARCHAR(500) NOT NULL,
    ADD COLUMN points INTEGER NOT NULL,
    ADD COLUMN is_active BOOLEAN NOT NULL,
    ADD CONSTRAINT uk_missions_description UNIQUE (description);

COMMIT;

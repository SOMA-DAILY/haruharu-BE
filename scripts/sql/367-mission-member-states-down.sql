BEGIN;

DROP INDEX IF EXISTS idx_room_missions_opens_at;
DROP TABLE IF EXISTS mission_member_states;

COMMIT;

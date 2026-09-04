BEGIN;

CREATE INDEX IF NOT EXISTS idx_room_members_active_user_room
    ON room_members (user_id, room_id)
    WHERE deleted_at IS NULL;

COMMIT;

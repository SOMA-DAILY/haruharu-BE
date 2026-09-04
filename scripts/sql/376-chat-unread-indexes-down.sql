BEGIN;

DROP INDEX IF EXISTS idx_chat_messages_room_created_id;
DROP INDEX IF EXISTS idx_chat_room_members_active_user_room;

-- GROUP membership backfill and joined_at correction are intentionally not reversed:
-- backfilled rows cannot be distinguished safely from runtime-created rows, and original
-- mismatched timestamps are unknown.

COMMIT;

BEGIN;
DO $$ BEGIN IF EXISTS (SELECT 1 FROM room_schedule_items) OR EXISTS (SELECT 1 FROM chat_messages WHERE message_type <> 'TEXT' OR admin_deleted_at IS NOT NULL) THEN RAISE EXCEPTION 'Room schedule/admin chat data exists; archive it before rollback'; END IF; END $$;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS delete_broadcast_at;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS admin_deleted_at;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS schedule_item_id;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS message_type;
DROP TABLE IF EXISTS room_schedule_items;
COMMIT;

BEGIN;
DO $$ BEGIN IF EXISTS (SELECT 1 FROM chat_message_reactions) OR EXISTS (SELECT 1 FROM chat_messages WHERE reply_to_message_id IS NOT NULL) THEN RAISE EXCEPTION 'Chat reaction/reply data exists; archive it before rollback'; END IF; END $$;
DROP INDEX IF EXISTS idx_chat_messages_reply_to;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS reply_preview_content;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS reply_preview_sender;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS reply_to_message_id;
DROP TABLE IF EXISTS chat_message_reactions;
COMMIT;

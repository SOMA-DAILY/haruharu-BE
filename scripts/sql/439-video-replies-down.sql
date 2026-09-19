BEGIN;
DO $$ BEGIN IF EXISTS (SELECT 1 FROM notification_history WHERE type='VIDEO_REPLY') OR EXISTS (SELECT 1 FROM chat_messages WHERE video_post_id IS NOT NULL) THEN RAISE EXCEPTION 'Video reply data exists; archive it before rollback'; END IF; END $$;
DROP INDEX IF EXISTS uq_chat_messages_reply_request;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS client_request_id;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS video_post_id;
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS notification_history_type_check;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'COIN_REFUNDED', 'MATCH_CONSOLATION', 'PAYMENT_COMPLETED', 'WINBACK'));
COMMIT;

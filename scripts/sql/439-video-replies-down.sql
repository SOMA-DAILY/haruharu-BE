BEGIN;
DO $$ BEGIN IF EXISTS (SELECT 1 FROM notification_history WHERE type='VIDEO_REPLY') OR EXISTS (SELECT 1 FROM chat_messages WHERE video_post_id IS NOT NULL) THEN RAISE EXCEPTION 'Video reply data exists; archive it before rollback'; END IF; END $$;
-- ddl-auto=update 로 Hibernate 가 먼저 만든 경우 인덱스가 아닌 unique 제약이므로 제약부터 제거한다.
ALTER TABLE chat_messages DROP CONSTRAINT IF EXISTS uq_chat_messages_reply_request;
DROP INDEX IF EXISTS uq_chat_messages_reply_request;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS client_request_id;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS video_post_id;
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS notification_history_type_check;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'QUESTION_ANSWER_DEADLINE_SOON', 'QUESTION_SEND_DEADLINE_SOON', 'COIN_REFUNDED', 'MATCH_CONSOLATION', 'PAYMENT_COMPLETED', 'WINBACK'));
COMMIT;

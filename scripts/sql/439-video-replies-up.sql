BEGIN;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS video_post_id bigint;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS client_request_id varchar(36);
CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_messages_reply_request ON chat_messages(chat_room_id, sender_id, client_request_id);
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS notification_history_type_check;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'VIDEO_REPLY', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'COIN_REFUNDED', 'MATCH_CONSOLATION', 'PAYMENT_COMPLETED', 'WINBACK'));
COMMIT;

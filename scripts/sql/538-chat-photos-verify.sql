SELECT column_name, data_type FROM information_schema.columns
WHERE table_name='chat_messages' AND column_name LIKE 'photo_%' ORDER BY column_name;
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid='chat_messages'::regclass AND conname IN ('ck_chat_messages_photo','ck_chat_messages_mission_reference','uq_chat_messages_reply_request');
SELECT count(*) AS invalid_photos FROM chat_messages WHERE message_type='IMAGE'
AND (photo_key IS NULL OR photo_width IS NULL OR photo_height IS NULL OR photo_digest IS NULL);

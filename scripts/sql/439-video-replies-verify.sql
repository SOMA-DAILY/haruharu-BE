SELECT column_name, data_type FROM information_schema.columns WHERE table_name='chat_messages' AND column_name IN ('video_post_id','client_request_id');
SELECT indexname FROM pg_indexes WHERE indexname='uq_chat_messages_reply_request';
SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='notification_history'::regclass AND conname='notification_history_type_check';

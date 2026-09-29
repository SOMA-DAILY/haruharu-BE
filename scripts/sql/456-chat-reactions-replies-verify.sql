SELECT column_name, data_type, character_maximum_length, is_nullable FROM information_schema.columns WHERE table_name='chat_message_reactions' ORDER BY ordinal_position;
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='chat_message_reactions'::regclass;
SELECT column_name, data_type, character_maximum_length FROM information_schema.columns WHERE table_name='chat_messages' AND column_name IN ('reply_to_message_id','reply_preview_sender','reply_preview_content');
SELECT indexname FROM pg_indexes WHERE indexname IN ('idx_chat_message_reactions_message','idx_chat_messages_reply_to');

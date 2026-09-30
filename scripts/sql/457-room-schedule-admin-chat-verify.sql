SELECT column_name, data_type, character_maximum_length, is_nullable, column_default FROM information_schema.columns WHERE table_name='room_schedule_items' ORDER BY ordinal_position;
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='room_schedule_items'::regclass;
SELECT indexname FROM pg_indexes WHERE tablename='room_schedule_items';
SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_name='chat_messages' AND column_name IN ('message_type','schedule_item_id','admin_deleted_at','delete_broadcast_at');
SELECT message_type, count(*) FROM chat_messages GROUP BY message_type;

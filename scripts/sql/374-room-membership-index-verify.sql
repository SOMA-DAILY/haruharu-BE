SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND tablename = 'room_members'
  AND indexname = 'idx_room_members_active_user_room';

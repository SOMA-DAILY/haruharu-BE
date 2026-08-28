SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name = 'room_mission_slots'
ORDER BY ordinal_position;

SELECT constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = current_schema()
  AND table_name = 'room_mission_slots'
ORDER BY constraint_name;

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND tablename = 'room_mission_slots'
ORDER BY indexname;

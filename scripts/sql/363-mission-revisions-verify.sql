SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name IN ('missions', 'mission_revisions', 'mission_revision_eligibilities', 'room_missions')
ORDER BY table_name, ordinal_position;

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = current_schema()
  AND table_name IN ('missions', 'mission_revisions', 'mission_revision_eligibilities', 'room_missions')
ORDER BY table_name, constraint_name;

SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND tablename IN ('missions', 'mission_revisions', 'mission_revision_eligibilities', 'room_missions')
ORDER BY tablename, indexname;

SELECT
    (SELECT COUNT(*) FROM missions) AS mission_count,
    (SELECT COUNT(*) FROM mission_revisions) AS revision_count,
    (SELECT COUNT(*) FROM mission_revision_eligibilities) AS eligibility_count,
    (SELECT COUNT(*) FROM room_missions) AS room_mission_count;

SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name IN ('room_missions', 'mission_assignment_incidents')
ORDER BY table_name, ordinal_position;

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = current_schema()
  AND table_name IN ('room_missions', 'mission_assignment_incidents')
ORDER BY table_name, constraint_name;

SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND tablename IN ('room_missions', 'mission_assignment_incidents')
ORDER BY tablename, indexname;

SELECT
    COUNT(*) FILTER (WHERE mission_revision_id IS NULL) AS missing_revision,
    COUNT(*) FILTER (WHERE room_mission_slot_id IS NULL) AS missing_slot,
    COUNT(*) FILTER (WHERE policy_version IS NULL) AS missing_policy,
    COUNT(*) FILTER (WHERE candidate_snapshot IS NULL) AS missing_candidate_snapshot
FROM room_missions;

SELECT status, reason, COUNT(*)
FROM mission_assignment_incidents
GROUP BY status, reason
ORDER BY status, reason;

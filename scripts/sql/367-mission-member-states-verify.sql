SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name = 'mission_member_states'
ORDER BY ordinal_position;

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = current_schema()
  AND table_name = 'mission_member_states'
ORDER BY constraint_name;

SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND (tablename = 'mission_member_states'
       OR indexname = 'idx_room_missions_opens_at')
ORDER BY indexname;

SELECT
    COUNT(*) FILTER (WHERE expected_activity_count < 0 OR cross_channel_activity_count < 0)
        AS invalid_activity_count,
    COUNT(*) FILTER (WHERE (resolution IS NULL) <> (resolved_at IS NULL))
        AS invalid_resolution_pair,
    COUNT(*) FILTER (WHERE
        (first_impressed_at IS NULL) <> (last_impressed_at IS NULL)
        OR first_impressed_at > last_impressed_at)
        AS invalid_impression_pair_or_order,
    COUNT(*) FILTER (WHERE room_mission_id IS NULL OR room_member_id IS NULL)
        AS missing_target
FROM mission_member_states;

SELECT room_mission_id, room_member_id, COUNT(*) AS duplicate_count
FROM mission_member_states
GROUP BY room_mission_id, room_member_id
HAVING COUNT(*) > 1;

SELECT state.id, state.room_mission_id, state.room_member_id
FROM mission_member_states state
LEFT JOIN room_missions mission ON mission.id = state.room_mission_id
LEFT JOIN room_members member ON member.id = state.room_member_id
WHERE mission.id IS NULL OR member.id IS NULL;

\set ON_ERROR_STOP on
\pset pager off
SELECT version();
SELECT seeded_at, room_count, active_rooms, members_per_room, messages_per_room FROM chat_perf_fixture;
SELECT count(*) AS current_message_count FROM chat_messages;
SELECT indexname, indexdef FROM pg_indexes
WHERE schemaname = 'public' AND tablename IN ('rooms', 'chat_rooms') ORDER BY tablename, indexname;
SELECT calls, round(total_exec_time::numeric, 2) AS total_ms,
       round(mean_exec_time::numeric, 3) AS mean_ms,
       rows, shared_blks_hit, shared_blks_read, query
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND query ILIKE '%rooms%' AND query NOT ILIKE '%pg_stat_statements%'
ORDER BY total_exec_time DESC LIMIT 15;

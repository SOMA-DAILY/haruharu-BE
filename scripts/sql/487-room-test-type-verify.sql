SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'rooms' AND column_name = 'is_test';

SELECT indexname, indexdef FROM pg_indexes
WHERE schemaname = current_schema() AND tablename = 'rooms' AND indexname = 'idx_rooms_is_test_id';

SELECT is_test, count(*) FROM rooms GROUP BY is_test ORDER BY is_test;

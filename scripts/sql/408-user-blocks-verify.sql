-- 테이블·컬럼 확인
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'user_blocks'
ORDER BY ordinal_position;

-- 제약·인덱스 확인 (uq_user_blocks_blocker_blocked, idx_user_blocks_blocker, idx_user_blocks_blocked)
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema() AND tablename = 'user_blocks';

-- 중복 쌍이 없어야 한다(0행)
SELECT blocker_user_id, blocked_user_id, count(*)
FROM user_blocks
GROUP BY blocker_user_id, blocked_user_id
HAVING count(*) > 1;

-- 인덱스 존재·정의 확인(partial predicate 포함).
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND indexname = 'idx_user_images_user_sorted_active';

-- 실제 목록 조회가 이 인덱스를 타는지 계획 확인(seq scan이 아닌 index scan 기대).
EXPLAIN
SELECT id, user_id, image_url, sorted_order
FROM user_images
WHERE user_id IN (1, 2, 3) AND deleted_at IS NULL
ORDER BY user_id, sorted_order;

-- 읽기 전용. 대상 환경에서 실행 후 제약/분포를 확인한다. 유저 ID나 본문은 출력하지 않는다.
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'chat_messages'
ORDER BY ordinal_position;

SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'chat_messages'::regclass OR confrelid = 'chat_messages'::regclass;

SELECT indexname, indexdef FROM pg_indexes
WHERE schemaname = current_schema() AND tablename = 'chat_messages';

SELECT message_type, sender_id IS NULL AS sender_missing, deleted_at IS NOT NULL AS soft_deleted, count(*)
FROM chat_messages GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;

SELECT count(*) AS duplicated_schedule_ids FROM (
    SELECT schedule_item_id FROM chat_messages WHERE schedule_item_id IS NOT NULL
    GROUP BY schedule_item_id HAVING count(*) > 1
) duplicates;

SELECT count(*) AS missing_sender_users FROM chat_messages m
WHERE m.sender_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = m.sender_id);

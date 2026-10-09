-- 모든 invalid/mismatched/duplicated 값은 0이어야 한다. 본문·개인 식별자는 출력하지 않는다.
SELECT sender_type, message_type, count(*) FROM chat_messages GROUP BY 1, 2 ORDER BY 1, 2;
SELECT count(*) AS invalid_sender_rows FROM chat_messages
WHERE sender_type IS NULL OR NOT (
    (sender_type = 'USER' AND sender_id IS NOT NULL)
    OR (sender_type = 'SYSTEM' AND sender_id IS NULL)
);
SELECT count(*) AS mismatched_migrated_rows
FROM chat_message_sender_backup_484 b LEFT JOIN chat_messages m ON m.id = b.id
WHERE m.id IS NULL OR m.sender_type IS DISTINCT FROM CASE WHEN b.message_type = 'ADMIN' THEN 'SYSTEM' ELSE 'USER' END
    OR m.sender_id IS DISTINCT FROM CASE WHEN b.message_type = 'ADMIN' THEN NULL ELSE b.sender_id END
    OR m.message_type IS DISTINCT FROM 'TEXT';
SELECT count(*) AS duplicated_schedule_ids FROM (
    SELECT schedule_item_id FROM chat_messages WHERE schedule_item_id IS NOT NULL
    GROUP BY schedule_item_id HAVING count(*) > 1
) duplicates;
SELECT column_name, is_nullable FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'chat_messages' AND column_name IN ('sender_id', 'sender_type');
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid = 'chat_messages'::regclass;

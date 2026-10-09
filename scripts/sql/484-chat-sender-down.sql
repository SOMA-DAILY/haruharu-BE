-- 쓰기 중단 후 실행. 전환 후 새 SYSTEM 메시지가 생겼으면 무손실 구버전 복귀가 불가능하므로 중단한다.
-- 계정/특수 ID를 만들어 채우거나 메시지를 삭제하지 않는다. 이 경우 새 버전에서 수정 배포한다.
BEGIN;
LOCK TABLE chat_messages IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM chat_messages m LEFT JOIN chat_message_sender_backup_484 b ON b.id = m.id
               WHERE m.sender_type = 'SYSTEM' AND (b.id IS NULL OR b.message_type <> 'ADMIN')) THEN
        RAISE EXCEPTION 'New system messages exist: keep the new schema and roll forward';
    END IF;
END $$;
ALTER TABLE chat_messages DROP CONSTRAINT ck_chat_messages_sender;
ALTER TABLE chat_messages DROP CONSTRAINT ck_chat_messages_content_type;
ALTER TABLE chat_messages DROP CONSTRAINT uq_chat_messages_schedule_item;
UPDATE chat_messages m SET sender_id = b.sender_id, message_type = b.message_type
FROM chat_message_sender_backup_484 b WHERE m.id = b.id;
ALTER TABLE chat_messages ALTER COLUMN sender_id SET NOT NULL;
ALTER TABLE chat_messages DROP COLUMN sender_type;
-- 원본 백업은 검증/보존 기간 결정 전까지 유지한다. 재전환 시 별도 보관 후 처리한다.
COMMIT;

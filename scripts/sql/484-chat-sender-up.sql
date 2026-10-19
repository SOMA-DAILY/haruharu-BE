-- #484. 구버전 app/admin 쓰기를 중단하고 preflight와 프런트 전환 조건을 확인한 뒤 1회 실행.
-- 재실행은 의도적으로 실패한다. 롤백용 원본 백업을 덮어쓰지 않는다.
BEGIN;
LOCK TABLE chat_messages IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM chat_messages
               WHERE message_type IS NULL OR message_type NOT IN ('TEXT', 'ADMIN') OR sender_id IS NULL) THEN
        RAISE EXCEPTION 'Unclassified legacy messages: inspect before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM chat_messages WHERE schedule_item_id IS NOT NULL
               GROUP BY schedule_item_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate schedule messages: inspect before migration';
    END IF;
END $$;

CREATE TABLE chat_message_sender_backup_484 AS
SELECT id, sender_id, message_type FROM chat_messages;
ALTER TABLE chat_message_sender_backup_484 ADD PRIMARY KEY (id);

ALTER TABLE chat_messages ADD COLUMN sender_type varchar(20);
ALTER TABLE chat_messages ALTER COLUMN sender_id DROP NOT NULL;
-- 운영 의미가 명시된 ADMIN만 전환. 특정 계정의 TEXT 메시지는 USER로 남긴다.
UPDATE chat_messages SET sender_type = CASE WHEN message_type = 'ADMIN' THEN 'SYSTEM' ELSE 'USER' END;
UPDATE chat_messages SET sender_id = NULL, message_type = 'TEXT' WHERE sender_type = 'SYSTEM';
ALTER TABLE chat_messages ALTER COLUMN sender_type SET NOT NULL;
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_sender CHECK (
    (sender_type = 'USER' AND sender_id IS NOT NULL)
    OR (sender_type = 'SYSTEM' AND sender_id IS NULL)
);
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_content_type CHECK (message_type = 'TEXT');
-- nullable sender_id를 포함한 reply_request 키와 독립적으로 편성당 한 메시지를 보장한다.
ALTER TABLE chat_messages ADD CONSTRAINT uq_chat_messages_schedule_item UNIQUE (schedule_item_id);
COMMIT;

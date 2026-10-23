-- #540 운영자가 정한 시간에만 열리는 1:1 대화.
--   미션 유형 PAIR_CHAT · 채팅방 유형 PAIR · 알림 유형 PAIR_CHAT 허용, chat_rooms.room_id 유일을 GROUP 한정으로 바꾼다.
-- 새 app·admin 을 띄우기 전에 적용한다. dev(ddl-auto=update)는 옛 서버 재시작이 옛 유니크를 되살리므로 새 서버 배포 뒤 다시 적용한다.
-- 여러 번 실행해도 같은 결과이고 기존 행은 바꾸지 않는다.
-- 옛 서버와 함께 돌아도 안전하다: 옛 서버는 새 유형 값을 쓰지 않고 room_id 를 GROUP 에만 쓴다.
BEGIN;
SET LOCAL lock_timeout = '5s';

DO $$
DECLARE target text; existing record;
BEGIN
    IF to_regclass('rooms') IS NULL OR to_regclass('chat_rooms') IS NULL
            OR to_regclass('room_mission_instances') IS NULL OR to_regclass('notification_history') IS NULL THEN
        RAISE EXCEPTION 'rooms, chat_rooms, room_mission_instances and notification_history must exist (run 486 first)';
    END IF;
    -- 값 목록 CHECK 는 Hibernate 가 만든 이름일 수 있다. 세 표 "type" 칸의 단일 칸 CHECK 를 모두 바꾼다.
    FOREACH target IN ARRAY ARRAY['room_mission_instances', 'chat_rooms', 'notification_history'] LOOP
        FOR existing IN
            SELECT c.conname FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'type'
            WHERE c.conrelid = target::regclass AND c.contype = 'c'
                AND cardinality(c.conkey) = 1 AND a.attnum = ANY(c.conkey)
        LOOP
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', target, existing.conname);
        END LOOP;
    END LOOP;
END $$;

ALTER TABLE room_mission_instances ADD CONSTRAINT room_mission_instances_type_check
    CHECK (type IN ('ANNOUNCEMENT', 'PROFILE_REVEAL', 'RECORDING', 'PAIR_CHAT'));
ALTER TABLE chat_rooms ADD CONSTRAINT chat_rooms_type_check
    CHECK (type IN ('GROUP', 'DIRECT', 'PAIR'));
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check
    CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'VIDEO_REPLY', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'QUESTION_ANSWER_DEADLINE_SOON', 'QUESTION_SEND_DEADLINE_SOON', 'COIN_REFUNDED', 'PAYMENT_COMPLETED', 'WINBACK', 'ADMIN_MESSAGE', 'PROFILE_REVEAL', 'CHAT_REPLY', 'PAIR_CHAT'));

-- PAIR 도 GROUP 처럼 chat_rooms.room_id 로 원본 방을 잇는다. 방당 1개는 GROUP 에만 해당하므로
-- 전체 유니크 uq_chat_rooms_room 을 GROUP 부분 유니크로 바꾼다. 옛 제약과 같은 범위(삭제된 행 포함)를 막는다.
-- 새 색인을 먼저 만들고 옛 제약을 지운다(한 트랜잭션이라 비는 순간이 없다).
-- 옛 제약은 운영에선 uq_chat_rooms_room 이지만 다른 이름일 수 있어 room_id 한 칸짜리 UNIQUE 를 모두 지운다.
CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_rooms_group_room ON chat_rooms (room_id) WHERE type = 'GROUP';
DO $$
DECLARE existing record;
BEGIN
    FOR existing IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'room_id'
        WHERE c.conrelid = 'chat_rooms'::regclass AND c.contype = 'u' AND c.conkey = ARRAY[a.attnum]
    LOOP
        EXECUTE format('ALTER TABLE chat_rooms DROP CONSTRAINT %I', existing.conname);
    END LOOP;
END $$;

COMMIT;

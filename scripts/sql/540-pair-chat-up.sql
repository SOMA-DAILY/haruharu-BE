-- #540 운영자가 정한 시간에만 열리는 1:1 대화.
--   미션 유형 PAIR_CHAT · 채팅방 유형 PAIR · 알림 유형 PAIR_CHAT 허용, 쌍 표 chat_room_pairs 생성.
-- 새 app(ddl-auto=validate)·admin(방 취소가 같은 트랜잭션에서 쌍 표를 읽는다)을 띄우기 전에 적용한다.
-- 여러 번 실행해도 같은 결과이고 기존 행은 바꾸지 않는다.
-- 옛 서버와 함께 돌아도 안전하다: 옛 서버는 새 유형 값을 쓰지 않고 새 표를 읽지 않는다.
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

-- PAIR 채팅방의 원본 방과 두 사람. chat_rooms.room_id 는 GROUP 1방 유일 제약이 있어 PAIR 는 비워 두고 여기서 잇는다.
-- 방별 조회는 유일 제약의 색인(room_id 가 앞 칸)을 쓰므로 room_id 색인을 따로 두지 않는다.
CREATE TABLE IF NOT EXISTS chat_room_pairs (
    chat_room_id bigint PRIMARY KEY,
    room_id bigint NOT NULL,
    low_user_id bigint NOT NULL,
    high_user_id bigint NOT NULL,
    created_at timestamp(6) NOT NULL,
    CONSTRAINT fk_chat_room_pairs_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_chat_room_pairs_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT ck_chat_room_pairs_order CHECK (low_user_id < high_user_id),
    CONSTRAINT uq_chat_room_pairs_room_users UNIQUE (room_id, low_user_id, high_user_id)
);

-- dev(ddl-auto=update)는 Hibernate 가 이 표를 먼저 만들 수 있다. 엔티티에 연관이 없어 그 표에는 외래 키가 없으므로
-- 위 CREATE 를 건너뛴 경우에도 빠진 외래 키만 붙인다. 같은 칸의 참조가 이미 있으면(이전 up 의 자동 이름 포함) 더하지 않는다.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'chat_room_pairs'::regclass AND contype = 'f' AND confrelid = 'chat_rooms'::regclass
          AND conkey = ARRAY[(SELECT attnum FROM pg_attribute
                              WHERE attrelid = 'chat_room_pairs'::regclass AND attname = 'chat_room_id')]) THEN
        ALTER TABLE chat_room_pairs ADD CONSTRAINT fk_chat_room_pairs_chat_room
            FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'chat_room_pairs'::regclass AND contype = 'f' AND confrelid = 'rooms'::regclass
          AND conkey = ARRAY[(SELECT attnum FROM pg_attribute
                              WHERE attrelid = 'chat_room_pairs'::regclass AND attname = 'room_id')]) THEN
        ALTER TABLE chat_room_pairs ADD CONSTRAINT fk_chat_room_pairs_room
            FOREIGN KEY (room_id) REFERENCES rooms(id);
    END IF;
END $$;

COMMIT;

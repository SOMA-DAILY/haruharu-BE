-- #540 적용 확인. up 직후 실행한다. 쌍 표나 그 외래 키가 빠졌으면 예외로 멈춘다.
DO $$
DECLARE problems text;
BEGIN
    IF to_regclass('chat_room_pairs') IS NULL THEN
        RAISE EXCEPTION 'chat_room_pairs is missing: run 540-pair-chat-up.sql';
    END IF;
    -- 외래 키: 만든 경로(up 의 CREATE, 먼저 생긴 표에 up 이 덧붙임, 이전 up)마다 이름이 달라 양쪽 칸·검증 완료로 본다.
    SELECT string_agg(e.col || ' -> ' || e.ref_tbl || '(id)', ', ') INTO problems
    FROM (VALUES ('chat_room_id', 'chat_rooms'), ('room_id', 'rooms')) AS e(col, ref_tbl)
    WHERE NOT EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.contype = 'f' AND c.conrelid = 'chat_room_pairs'::regclass AND c.confrelid = e.ref_tbl::regclass
          AND c.conkey = ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid = 'chat_room_pairs'::regclass AND attname = e.col)]
          AND c.confkey = ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid = e.ref_tbl::regclass AND attname = 'id')]
          AND c.convalidated);
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'chat_room_pairs foreign keys missing (run 540-pair-chat-up.sql): %', problems;
    END IF;
END $$;
-- 세 CHECK 의 허용 목록에 새 값이 있고, 쌍 표의 제약·외래 키가 있어야 한다.
SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conname IN ('room_mission_instances_type_check', 'chat_rooms_type_check', 'notification_history_type_check',
                  'ck_chat_room_pairs_order', 'uq_chat_room_pairs_room_users')
   OR (conrelid = 'chat_room_pairs'::regclass AND contype = 'f')
ORDER BY conname;
-- 쌍 없는 PAIR 채팅방(0 이어야 한다).
SELECT count(*) AS orphan_pair_rooms FROM chat_rooms c
WHERE c.type = 'PAIR' AND NOT EXISTS (SELECT 1 FROM chat_room_pairs p WHERE p.chat_room_id = c.id);

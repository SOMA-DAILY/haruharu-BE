-- #540 적용 확인. up 직후 실행한다. room_id 유일이 GROUP 한정이 아니면 예외로 멈춘다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'room_id'
        WHERE c.conrelid = 'chat_rooms'::regclass AND c.contype = 'u' AND c.conkey = ARRAY[a.attnum]) THEN
        RAISE EXCEPTION 'chat_rooms.room_id still has a full UNIQUE (PAIR cannot share room_id): run 540-pair-chat-up.sql';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
        WHERE c.relname = 'ux_chat_rooms_group_room' AND i.indrelid = 'chat_rooms'::regclass AND i.indisunique
          AND i.indisvalid AND pg_get_expr(i.indpred, i.indrelid) LIKE '%''GROUP''%') THEN
        RAISE EXCEPTION 'ux_chat_rooms_group_room (UNIQUE room_id WHERE type = GROUP) is missing: run 540-pair-chat-up.sql';
    END IF;
END $$;
-- 세 CHECK 의 허용 목록에 새 값이 있어야 한다.
SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conname IN ('room_mission_instances_type_check', 'chat_rooms_type_check', 'notification_history_type_check')
ORDER BY conname;
-- 방마다 GROUP 은 1개 이하여야 한다(0 행이어야 한다).
SELECT room_id, count(*) FROM chat_rooms WHERE type = 'GROUP' AND room_id IS NOT NULL GROUP BY room_id HAVING count(*) > 1;

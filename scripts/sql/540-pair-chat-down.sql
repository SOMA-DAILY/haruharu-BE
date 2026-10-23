-- #540 되돌리기. 1:1 대화 데이터(PAIR_CHAT 미션·PAIR 채팅방·PAIR_CHAT 알림)가 하나라도 있으면 멈춘다.
-- 행을 지우지 않는다. 데이터가 있으면 보관·정리 방법을 먼저 정한다.
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM room_mission_instances WHERE type = 'PAIR_CHAT')
            OR EXISTS (SELECT 1 FROM chat_rooms WHERE type = 'PAIR')
            OR EXISTS (SELECT 1 FROM notification_history WHERE type = 'PAIR_CHAT') THEN
        RAISE EXCEPTION 'Pair chat data exists; manual reconciliation required';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'chat_rooms'::regclass AND conname = 'uq_chat_rooms_room') THEN
        ALTER TABLE chat_rooms ADD CONSTRAINT uq_chat_rooms_room UNIQUE (room_id);
    END IF;
END $$;
DROP INDEX IF EXISTS ux_chat_rooms_group_room;
ALTER TABLE room_mission_instances DROP CONSTRAINT IF EXISTS room_mission_instances_type_check;
ALTER TABLE room_mission_instances ADD CONSTRAINT room_mission_instances_type_check
    CHECK (type IN ('ANNOUNCEMENT', 'PROFILE_REVEAL', 'RECORDING'));
ALTER TABLE chat_rooms DROP CONSTRAINT IF EXISTS chat_rooms_type_check;
ALTER TABLE chat_rooms ADD CONSTRAINT chat_rooms_type_check CHECK (type IN ('GROUP', 'DIRECT'));
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS notification_history_type_check;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'VIDEO_REPLY', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'QUESTION_ANSWER_DEADLINE_SOON', 'QUESTION_SEND_DEADLINE_SOON', 'COIN_REFUNDED', 'PAYMENT_COMPLETED', 'WINBACK', 'ADMIN_MESSAGE', 'PROFILE_REVEAL', 'CHAT_REPLY'));
COMMIT;

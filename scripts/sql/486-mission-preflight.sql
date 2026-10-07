-- #486: 읽기 전용. #484 전환 완료 상태에서 app/admin의 쓰기와 기존 잡을 중단한 뒤 다시 확인한다.
-- 아래 DO가 실패하면 up을 실행하지 않는다. SENDING은 기존 실행기의 결과를 확정한 뒤 0건이어야 한다.
SELECT status, count(*) FROM room_schedule_items GROUP BY status ORDER BY status;
SELECT count(*) AS linked_messages FROM chat_messages WHERE schedule_item_id IS NOT NULL;
DO $$
BEGIN
    IF to_regclass('room_mission_instances') IS NOT NULL OR to_regclass('mission_announcements') IS NOT NULL
        OR to_regclass('announcement_missions') IS NOT NULL THEN
        RAISE EXCEPTION '486 target tables already exist; inspect migration state';
    END IF;
    IF EXISTS (SELECT 1 FROM room_schedule_items WHERE status = 'SENDING') THEN
        RAISE EXCEPTION 'Resolve in-flight schedule items before cutover';
    END IF;
    IF EXISTS (SELECT 1 FROM room_schedule_items s LEFT JOIN rooms r ON r.id=s.room_id WHERE r.id IS NULL) THEN
        RAISE EXCEPTION 'Orphan schedule room';
    END IF;
    IF EXISTS (SELECT 1 FROM chat_messages WHERE message_type <> 'TEXT' OR sender_type NOT IN ('USER','SYSTEM')) THEN
        RAISE EXCEPTION 'Complete 484 sender migration first';
    END IF;
    IF EXISTS (SELECT 1 FROM chat_messages c LEFT JOIN room_schedule_items s ON s.id=c.schedule_item_id
        LEFT JOIN chat_rooms cr ON cr.id=c.chat_room_id
        WHERE c.schedule_item_id IS NOT NULL AND (s.id IS NULL OR c.sender_type <> 'SYSTEM' OR c.sender_id IS NOT NULL
            OR cr.type <> 'GROUP' OR cr.room_id IS DISTINCT FROM s.room_id
            OR s.sent_message_id IS DISTINCT FROM c.id OR s.status <> 'SENT')) THEN
        RAISE EXCEPTION 'Unclassified schedule-linked message; do not guess ownership';
    END IF;
    IF EXISTS (SELECT 1 FROM room_schedule_items s LEFT JOIN chat_messages c ON c.id=s.sent_message_id
        WHERE s.sent_message_id IS NOT NULL AND (c.id IS NULL OR c.schedule_item_id IS DISTINCT FROM s.id)) THEN
        RAISE EXCEPTION 'Schedule/message reference mismatch';
    END IF;
    IF EXISTS (SELECT 1 FROM room_schedule_items WHERE reveal_fields IS NOT NULL
        AND reveal_fields !~ '^(MBTI|INTO_NOW|AGE|DEPARTMENT)(,(MBTI|INTO_NOW|AGE|DEPARTMENT))*$') THEN
        RAISE EXCEPTION 'Unknown profile reveal fields';
    END IF;
END $$;

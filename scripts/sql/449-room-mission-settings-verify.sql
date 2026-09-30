-- 테이블·컬럼 확인
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'room_mission_settings'
ORDER BY ordinal_position;

-- 방당 1행 유니크 제약 확인 (uq_room_mission_settings_room)
SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'room_mission_settings'::regclass AND contype = 'u';

-- 설정 행이 없는 방(0행이어야 한다 — 없으면 미션 꺼짐으로 판정된다)
SELECT r.id
FROM rooms r
LEFT JOIN room_mission_settings s ON s.room_id = r.id
WHERE s.id IS NULL;

-- 켜짐인데 슬롯이 없거나, 꺼짐인데 슬롯이 있는 방(0행이어야 한다)
SELECT s.room_id, s.mission_enabled, count(slot.id) AS slot_count
FROM room_mission_settings s
LEFT JOIN room_mission_slots slot ON slot.room_id = s.room_id AND slot.deleted_at IS NULL
GROUP BY s.room_id, s.mission_enabled
HAVING s.mission_enabled <> (count(slot.id) > 0);

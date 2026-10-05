BEGIN;

-- 이전 버전 앱은 이 테이블을 읽지 않는다. 미션 꺼짐으로 만든 방은 슬롯이 없어 롤백 후에도 미션이 배정되지 않는다.
DROP TABLE IF EXISTS room_mission_settings;

COMMIT;

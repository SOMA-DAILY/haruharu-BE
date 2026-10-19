-- 격리된 PostgreSQL 테스트용 과거 설정·슬롯. 실제 환경에 실행하지 않는다.
CREATE TABLE room_mission_settings (
    id BIGSERIAL PRIMARY KEY, room_id BIGINT NOT NULL UNIQUE, mission_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP
);
CREATE TABLE room_mission_slots (
    id BIGSERIAL PRIMARY KEY, room_id BIGINT NOT NULL, day_number INTEGER NOT NULL,
    period VARCHAR(20) NOT NULL, opens_at TIMESTAMP NOT NULL, closes_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP
);
INSERT INTO room_mission_settings (room_id, mission_enabled, created_at, updated_at)
VALUES (482, true, '2026-01-01', '2026-01-01');
INSERT INTO room_mission_slots (room_id, day_number, period, opens_at, closes_at, created_at, updated_at)
VALUES (482, 1, 'MORNING', '2030-01-01T20:00:00', '2030-01-02T01:00:00', '2026-01-01', '2026-01-01');

-- 읽기 전용 현황 SQL의 선택 컬럼 조회·상태 집계 분기 검증용.
CREATE TABLE operator_missions (
    id BIGSERIAL PRIMARY KEY, status VARCHAR(20), delete_requested_at TIMESTAMP, delete_processed_at TIMESTAMP
);
INSERT INTO operator_missions(status, delete_requested_at) VALUES ('SCHEDULED', NULL), ('SENT', '2026-01-01');

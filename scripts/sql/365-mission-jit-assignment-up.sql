BEGIN;

-- 기존 행은 room_mission_slot_id와 결정 snapshot이 없어 새 계약으로 재현할 수 없다.
-- #361~#365 전환 기간의 미션 배정 데이터는 합의한 초기화 정책에 따라 폐기한다.
TRUNCATE TABLE room_missions RESTART IDENTITY;

ALTER TABLE room_missions
    ADD COLUMN room_mission_slot_id BIGINT NOT NULL,
    ADD COLUMN policy_version VARCHAR(40) NOT NULL,
    ADD COLUMN catalog_version VARCHAR(64) NOT NULL,
    ADD COLUMN context_schema_version VARCHAR(40) NOT NULL,
    ADD COLUMN context_as_of TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ADD COLUMN context_snapshot JSONB NOT NULL,
    ADD COLUMN candidate_schema_version VARCHAR(40) NOT NULL,
    ADD COLUMN candidate_snapshot JSONB NOT NULL,
    ADD COLUMN selected_probability NUMERIC(14, 12) NOT NULL,
    ADD COLUMN exploration_id UUID NOT NULL,
    ADD COLUMN rng_seed BIGINT NOT NULL,
    ADD COLUMN rng_algorithm VARCHAR(40) NOT NULL,
    ADD COLUMN relaxation_level VARCHAR(24) NOT NULL,
    ADD COLUMN application_commit_sha VARCHAR(64) NOT NULL,
    ADD COLUMN assigned_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ADD COLUMN assignment_delay_seconds BIGINT NOT NULL,
    ADD CONSTRAINT fk_room_missions_slot
        FOREIGN KEY (room_mission_slot_id) REFERENCES room_mission_slots (id),
    ADD CONSTRAINT uq_room_missions_slot UNIQUE (room_mission_slot_id),
    ADD CONSTRAINT ck_room_missions_probability
        CHECK (selected_probability > 0.0 AND selected_probability <= 1.0),
    ADD CONSTRAINT ck_room_missions_relaxation
        CHECK (relaxation_level IN ('NONE', 'SAFE_FALLBACK')),
    ADD CONSTRAINT ck_room_missions_assignment_delay
        CHECK (assignment_delay_seconds >= 0);

CREATE INDEX idx_room_mission_slots_closes_at
    ON room_mission_slots (closes_at);

CREATE TABLE mission_assignment_incidents (
    id BIGSERIAL PRIMARY KEY,
    room_mission_slot_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(48) NOT NULL,
    attempt_count INTEGER NOT NULL,
    first_failed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_failed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITHOUT TIME ZONE,
    last_error_summary VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_mission_assignment_incidents_slot
        FOREIGN KEY (room_mission_slot_id) REFERENCES room_mission_slots (id),
    CONSTRAINT uq_mission_assignment_incidents_slot UNIQUE (room_mission_slot_id),
    CONSTRAINT ck_mission_assignment_incidents_status
        CHECK (status IN ('RETRYING', 'DEGRADED', 'FAILED', 'RESOLVED')),
    CONSTRAINT ck_mission_assignment_incidents_reason
        CHECK (reason IN (
            'SAFE_FALLBACK_USED',
            'SAFE_FALLBACK_MISSING',
            'ASSIGNMENT_PERSISTENCE_FAILED',
            'ASSIGNMENT_DEADLINE_MISSED',
            'UNEXPECTED_ASSIGNMENT_ERROR'
        )),
    CONSTRAINT ck_mission_assignment_incidents_attempt_count
        CHECK (attempt_count > 0)
);

CREATE INDEX idx_mission_assignment_incidents_status_last_failed
    ON mission_assignment_incidents (status, last_failed_at);

COMMIT;

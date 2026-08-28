BEGIN;

CREATE TABLE room_mission_slots (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    day_number INTEGER NOT NULL,
    period VARCHAR(20) NOT NULL,
    opens_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    closes_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_room_mission_slots_room
        FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT ck_room_mission_slots_day_positive
        CHECK (day_number > 0),
    CONSTRAINT ck_room_mission_slots_period
        CHECK (period IN ('MORNING', 'AFTERNOON')),
    CONSTRAINT ck_room_mission_slots_window
        CHECK (opens_at < closes_at),
    CONSTRAINT uq_room_mission_slots_room_day_period
        UNIQUE (room_id, day_number, period)
);

CREATE INDEX idx_room_mission_slots_opens_at
    ON room_mission_slots (opens_at);

COMMIT;

BEGIN;

-- #363은 기존 미션 카탈로그·배정 데이터를 폐기하고 빈 카탈로그로 시작한다.
TRUNCATE TABLE room_missions, missions RESTART IDENTITY CASCADE;

ALTER TABLE room_missions
    DROP COLUMN mission_id;

ALTER TABLE missions
    DROP COLUMN description,
    DROP COLUMN points,
    DROP COLUMN is_active,
    ADD COLUMN active_revision_id BIGINT,
    ADD COLUMN active BOOLEAN NOT NULL;

CREATE TABLE mission_revisions (
    id BIGSERIAL PRIMARY KEY,
    mission_id BIGINT NOT NULL,
    revision_number INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL,
    disclosure_depth VARCHAR(16) NOT NULL,
    mission_goal VARCHAR(20) NOT NULL,
    activation_channel VARCHAR(20) NOT NULL,
    interaction_scope VARCHAR(20) NOT NULL,
    participation_burden VARCHAR(16) NOT NULL,
    privacy_risk VARCHAR(16) NOT NULL,
    preference_leak_risk VARCHAR(16) NOT NULL,
    capture_difficulty VARCHAR(16) NOT NULL,
    semantic_cluster VARCHAR(50) NOT NULL,
    base_quality_score NUMERIC(4, 3) NOT NULL,
    is_fallback BOOLEAN NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_mission_revisions_mission
        FOREIGN KEY (mission_id) REFERENCES missions (id),
    CONSTRAINT uq_mission_revisions_mission_number
        UNIQUE (mission_id, revision_number),
    CONSTRAINT ck_mission_revisions_number_positive
        CHECK (revision_number > 0),
    CONSTRAINT ck_mission_revisions_disclosure_depth
        CHECK (disclosure_depth IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_mission_revisions_goal
        CHECK (mission_goal IN ('OBSERVATION', 'PREFERENCE', 'MEANING', 'VALUE', 'PERSONAL', 'RELATIONAL')),
    CONSTRAINT ck_mission_revisions_channel
        CHECK (activation_channel IN ('VIDEO', 'GROUP_CHAT')),
    CONSTRAINT ck_mission_revisions_scope
        CHECK (interaction_scope IN ('SELF', 'GROUP', 'SPECIFIC_MEMBER')),
    CONSTRAINT ck_mission_revisions_burden
        CHECK (participation_burden IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_mission_revisions_privacy
        CHECK (privacy_risk IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_mission_revisions_preference_leak
        CHECK (preference_leak_risk IN ('NONE', 'LOW', 'HIGH')),
    CONSTRAINT ck_mission_revisions_capture
        CHECK (capture_difficulty IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_mission_revisions_cluster
        CHECK (semantic_cluster ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_mission_revisions_quality
        CHECK (base_quality_score BETWEEN 0.0 AND 1.0),
    CONSTRAINT ck_mission_revisions_chat_capture
        CHECK (activation_channel <> 'GROUP_CHAT' OR capture_difficulty = 'LOW'),
    CONSTRAINT ck_mission_revisions_safe_fallback
        CHECK (NOT is_fallback OR (
            disclosure_depth = 'LOW'
            AND participation_burden = 'LOW'
            AND privacy_risk = 'LOW'
            AND preference_leak_risk = 'NONE'
        ))
);

CREATE TABLE mission_revision_eligibilities (
    id BIGSERIAL PRIMARY KEY,
    mission_revision_id BIGINT NOT NULL,
    day_number INTEGER NOT NULL,
    period VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_mission_revision_eligibilities_revision
        FOREIGN KEY (mission_revision_id) REFERENCES mission_revisions (id),
    CONSTRAINT uq_mission_revision_eligibility_slot
        UNIQUE (mission_revision_id, day_number, period),
    CONSTRAINT ck_mission_revision_eligibilities_day
        CHECK (day_number BETWEEN 1 AND 3),
    CONSTRAINT ck_mission_revision_eligibilities_period
        CHECK (period IN ('MORNING', 'AFTERNOON'))
);

ALTER TABLE missions
    ADD CONSTRAINT fk_missions_active_revision
        FOREIGN KEY (active_revision_id) REFERENCES mission_revisions (id);

ALTER TABLE room_missions
    ADD COLUMN mission_revision_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_room_missions_revision
        FOREIGN KEY (mission_revision_id) REFERENCES mission_revisions (id);

CREATE INDEX idx_mission_revisions_mission
    ON mission_revisions (mission_id);

CREATE INDEX idx_mission_revision_eligibilities_revision
    ON mission_revision_eligibilities (mission_revision_id);

CREATE INDEX idx_room_missions_revision
    ON room_missions (mission_revision_id);

COMMIT;

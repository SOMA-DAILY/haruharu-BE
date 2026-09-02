BEGIN;

CREATE TABLE mission_member_states (
    id BIGSERIAL PRIMARY KEY,
    room_mission_id BIGINT NOT NULL,
    room_member_id BIGINT NOT NULL,
    expected_channel VARCHAR(20) NOT NULL,
    first_impressed_at TIMESTAMP WITHOUT TIME ZONE,
    last_impressed_at TIMESTAMP WITHOUT TIME ZONE,
    expected_activity_count INTEGER NOT NULL DEFAULT 0,
    first_expected_activity_at TIMESTAMP WITHOUT TIME ZONE,
    last_expected_activity_at TIMESTAMP WITHOUT TIME ZONE,
    cross_channel_activity_count INTEGER NOT NULL DEFAULT 0,
    first_cross_channel_activity_at TIMESTAMP WITHOUT TIME ZONE,
    last_cross_channel_activity_at TIMESTAMP WITHOUT TIME ZONE,
    resolution VARCHAR(32),
    resolved_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_mission_member_states_room_mission
        FOREIGN KEY (room_mission_id) REFERENCES room_missions (id),
    CONSTRAINT fk_mission_member_states_room_member
        FOREIGN KEY (room_member_id) REFERENCES room_members (id),
    CONSTRAINT uq_mission_member_states_mission_member
        UNIQUE (room_mission_id, room_member_id),
    CONSTRAINT ck_mission_member_states_expected_channel
        CHECK (expected_channel IN ('VIDEO', 'GROUP_CHAT')),
    CONSTRAINT ck_mission_member_states_resolution
        CHECK (resolution IS NULL OR resolution IN (
            'DISMISSED', 'VIDEO_UPLOAD_AFTER_VIEW', 'CHAT_MESSAGE_AFTER_VIEW')),
    CONSTRAINT ck_mission_member_states_resolution_pair
        CHECK ((resolution IS NULL) = (resolved_at IS NULL)),
    CONSTRAINT ck_mission_member_states_impression_order
        CHECK ((first_impressed_at IS NULL AND last_impressed_at IS NULL) OR (
            first_impressed_at IS NOT NULL AND last_impressed_at IS NOT NULL
                AND first_impressed_at <= last_impressed_at)),
    CONSTRAINT ck_mission_member_states_expected_activity
        CHECK (expected_activity_count >= 0 AND (
            (expected_activity_count = 0 AND first_expected_activity_at IS NULL AND last_expected_activity_at IS NULL)
            OR (expected_activity_count > 0 AND first_expected_activity_at IS NOT NULL
                AND last_expected_activity_at IS NOT NULL
                AND first_expected_activity_at <= last_expected_activity_at))),
    CONSTRAINT ck_mission_member_states_cross_activity
        CHECK (cross_channel_activity_count >= 0 AND (
            (cross_channel_activity_count = 0 AND first_cross_channel_activity_at IS NULL
                AND last_cross_channel_activity_at IS NULL)
            OR (cross_channel_activity_count > 0 AND first_cross_channel_activity_at IS NOT NULL
                AND last_cross_channel_activity_at IS NOT NULL
                AND first_cross_channel_activity_at <= last_cross_channel_activity_at)))
);

CREATE INDEX idx_mission_member_states_member_resolution
    ON mission_member_states (room_member_id, resolved_at);

CREATE INDEX idx_room_missions_opens_at
    ON room_missions (opens_at);

-- 배포 전 이미 열린 미션 이력은 migration에서 한 번 backfill하고,
-- 런타임 배치는 최근 3일·batchSize 범위만 catch-up한다.
INSERT INTO mission_member_states (
    room_mission_id, room_member_id, expected_channel,
    expected_activity_count, cross_channel_activity_count,
    created_at, updated_at)
SELECT rm.id, member.id, revision.activation_channel,
       0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM room_missions rm
JOIN mission_revisions revision ON revision.id = rm.mission_revision_id
JOIN room_members member ON member.room_id = rm.room_id
WHERE rm.opens_at <= CURRENT_TIMESTAMP
  AND member.joined_at <= rm.opens_at
  AND rm.deleted_at IS NULL
  AND member.deleted_at IS NULL
ON CONFLICT (room_mission_id, room_member_id) DO NOTHING;

COMMIT;

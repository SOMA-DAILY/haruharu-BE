-- #541. Apply before deploying the API; feature stays OFF until separately enabled.
-- Historical/cancelled reactions are intentionally not guessed or backfilled.
BEGIN;
CREATE TABLE IF NOT EXISTS room_interaction_experiments (
    room_id bigint PRIMARY KEY REFERENCES rooms(id) ON DELETE CASCADE,
    experiment_key varchar(100) NOT NULL,
    variant varchar(20) NOT NULL CHECK (variant IN ('CONTROL', 'TOOLTIP')),
    assigned_at timestamp NOT NULL
);
CREATE TABLE IF NOT EXISTS room_interaction_first_actions (
    room_member_id bigint PRIMARY KEY REFERENCES room_members(id) ON DELETE CASCADE,
    first_reacted_at timestamp,
    first_video_replied_at timestamp
);
CREATE TABLE IF NOT EXISTS room_interaction_guide_claims (
    id varchar(36) PRIMARY KEY,
    room_member_id bigint NOT NULL REFERENCES room_members(id) ON DELETE CASCADE,
    kind varchar(20) NOT NULL CHECK (kind IN ('REACTION', 'REPLY')),
    -- Preserve the original target ID even if a media record is physically removed later.
    video_post_id bigint NOT NULL,
    viewer_session_id varchar(100) NOT NULL CHECK (length(trim(viewer_session_id)) > 0),
    claimed_at timestamp NOT NULL,
    CONSTRAINT uk_interaction_guide_member_kind UNIQUE (room_member_id, kind),
    CONSTRAINT uk_interaction_guide_member_session UNIQUE (room_member_id, viewer_session_id)
);
COMMIT;

BEGIN;

-- outcome 확정 배치가 시간창 종료 시 미해소 카드에 부여하는 상태.
ALTER TABLE mission_member_states
    DROP CONSTRAINT ck_mission_member_states_resolution;

ALTER TABLE mission_member_states
    ADD CONSTRAINT ck_mission_member_states_resolution
        CHECK (resolution IS NULL OR resolution IN (
            'DISMISSED', 'VIDEO_UPLOAD_AFTER_VIEW', 'CHAT_MESSAGE_AFTER_VIEW', 'EXPIRED'));

-- 확정된 미션 성과 스냅샷. 집계식이 바뀌면 UPDATE 하지 않고 새 metric_schema_version 행을 추가한다.
CREATE TABLE mission_outcomes (
    id BIGSERIAL PRIMARY KEY,
    room_mission_id BIGINT NOT NULL,
    metric_schema_version VARCHAR(32) NOT NULL,
    target_member_count INTEGER NOT NULL,
    impressed_member_count INTEGER NOT NULL,
    activated_member_count INTEGER NOT NULL,
    expected_channel_activated_count INTEGER NOT NULL,
    cross_channel_activated_count INTEGER NOT NULL,
    dismissed_member_count INTEGER NOT NULL,
    expired_member_count INTEGER NOT NULL,
    room_activation_coverage NUMERIC(5, 4) NOT NULL,
    exposure_reach NUMERIC(5, 4) NOT NULL,
    closes_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    calculated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_mission_outcomes_room_mission
        FOREIGN KEY (room_mission_id) REFERENCES room_missions (id),
    -- 재집계 멱등의 최종 방어선. 동시 실행이 같은 버전을 두 번 확정할 수 없다.
    CONSTRAINT uq_mission_outcomes_mission_metric_version
        UNIQUE (room_mission_id, metric_schema_version),
    CONSTRAINT ck_mission_outcomes_counts_non_negative
        CHECK (target_member_count >= 0
            AND impressed_member_count >= 0
            AND activated_member_count >= 0
            AND expected_channel_activated_count >= 0
            AND cross_channel_activated_count >= 0
            AND dismissed_member_count >= 0
            AND expired_member_count >= 0),
    -- 부분 카운트는 대상 멤버 수를 넘을 수 없다.
    CONSTRAINT ck_mission_outcomes_counts_within_target
        CHECK (impressed_member_count <= target_member_count
            AND activated_member_count <= target_member_count
            AND expected_channel_activated_count <= target_member_count
            AND cross_channel_activated_count <= target_member_count
            AND dismissed_member_count <= target_member_count
            AND expired_member_count <= target_member_count),
    CONSTRAINT ck_mission_outcomes_ratio_range
        CHECK (room_activation_coverage >= 0 AND room_activation_coverage <= 1
            AND exposure_reach >= 0 AND exposure_reach <= 1),
    -- 대상이 0명이면 비율도 0이어야 한다(분모 0 방어와 짝).
    CONSTRAINT ck_mission_outcomes_zero_target_zero_ratio
        CHECK (target_member_count > 0
            OR (room_activation_coverage = 0 AND exposure_reach = 0))
);

-- 확정 이후 기간별 조회용.
CREATE INDEX idx_mission_outcomes_closes_at
    ON mission_outcomes (closes_at);

COMMIT;

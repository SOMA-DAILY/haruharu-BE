BEGIN;

-- 신규 결정 로그 없이는 구 배정 행을 복원할 수 없으므로 롤백 시에도 배정 인스턴스를 초기화한다.
TRUNCATE TABLE room_missions RESTART IDENTITY;

DROP TABLE IF EXISTS mission_assignment_incidents;

DROP INDEX IF EXISTS idx_room_mission_slots_closes_at;

ALTER TABLE room_missions
    DROP CONSTRAINT IF EXISTS fk_room_missions_slot,
    DROP CONSTRAINT IF EXISTS uq_room_missions_slot,
    DROP CONSTRAINT IF EXISTS ck_room_missions_probability,
    DROP CONSTRAINT IF EXISTS ck_room_missions_relaxation,
    DROP CONSTRAINT IF EXISTS ck_room_missions_assignment_delay,
    DROP COLUMN room_mission_slot_id,
    DROP COLUMN policy_version,
    DROP COLUMN catalog_version,
    DROP COLUMN context_schema_version,
    DROP COLUMN context_as_of,
    DROP COLUMN context_snapshot,
    DROP COLUMN candidate_schema_version,
    DROP COLUMN candidate_snapshot,
    DROP COLUMN selected_probability,
    DROP COLUMN exploration_id,
    DROP COLUMN rng_seed,
    DROP COLUMN rng_algorithm,
    DROP COLUMN relaxation_level,
    DROP COLUMN application_commit_sha,
    DROP COLUMN assigned_at,
    DROP COLUMN assignment_delay_seconds;

COMMIT;

BEGIN;

-- 되돌린 CHECK 가 허용하지 않는 값을 먼저 정리한다. 완화 단계를 잃는 것은 감수하되,
-- 어떤 단계였는지는 candidate_snapshot 과 로그에 남아 있어 사후 추적은 가능하다.
UPDATE room_missions
SET relaxation_level = 'NONE',
    updated_at = CURRENT_TIMESTAMP
WHERE relaxation_level IN
      ('CLUSTER_RELAXED', 'FORMAT_RELAXED', 'BURDEN_RELAXED', 'REUSE_RELAXED');

ALTER TABLE room_missions
    DROP CONSTRAINT ck_room_missions_relaxation;

ALTER TABLE room_missions
    ADD CONSTRAINT ck_room_missions_relaxation
        CHECK (relaxation_level IN ('NONE', 'SAFE_FALLBACK'));

COMMIT;

BEGIN;

-- Rule V1 이 단계적 완화를 도입하면서 relaxation_level 값이 2개에서 6개로 늘었다.
-- 365 에서 만든 CHECK 는 NONE / SAFE_FALLBACK 만 허용하므로, 완화가 처음 일어나는 순간
-- INSERT 가 거부되어 배정 트랜잭션이 통째로 롤백된다.
-- Hibernate 는 CHECK 제약을 생성·수정하지 않으므로(@Check 미사용) ddl-auto 로는 절대 갱신되지 않는다.
ALTER TABLE room_missions
    DROP CONSTRAINT ck_room_missions_relaxation;

ALTER TABLE room_missions
    ADD CONSTRAINT ck_room_missions_relaxation
        CHECK (relaxation_level IN (
            'NONE',
            'CLUSTER_RELAXED',
            'FORMAT_RELAXED',
            'BURDEN_RELAXED',
            'REUSE_RELAXED',
            'SAFE_FALLBACK'));

COMMIT;

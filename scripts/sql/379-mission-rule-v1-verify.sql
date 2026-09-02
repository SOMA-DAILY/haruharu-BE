-- 제약이 6개 값을 모두 허용하는지.
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conname = 'ck_room_missions_relaxation';

-- 허용 목록에 새 값이 모두 들어갔는지 한눈에 확인.
SELECT
    pg_get_constraintdef(oid) LIKE '%CLUSTER_RELAXED%' AS allows_cluster,
    pg_get_constraintdef(oid) LIKE '%FORMAT_RELAXED%'  AS allows_format,
    pg_get_constraintdef(oid) LIKE '%BURDEN_RELAXED%'  AS allows_burden,
    pg_get_constraintdef(oid) LIKE '%REUSE_RELAXED%'   AS allows_reuse
FROM pg_constraint
WHERE conname = 'ck_room_missions_relaxation';

-- 실제로 저장된 완화 단계 분포. 완화가 잦으면 카탈로그 보강 신호다.
SELECT relaxation_level, COUNT(*) AS count
FROM room_missions
GROUP BY relaxation_level
ORDER BY count DESC;

-- 허용 목록 밖의 값이 남아 있으면 안 된다.
SELECT id, relaxation_level
FROM room_missions
WHERE relaxation_level NOT IN (
    'NONE', 'CLUSTER_RELAXED', 'FORMAT_RELAXED', 'BURDEN_RELAXED', 'REUSE_RELAXED', 'SAFE_FALLBACK');

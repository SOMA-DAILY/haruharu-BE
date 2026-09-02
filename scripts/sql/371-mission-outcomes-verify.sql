SELECT table_name, column_name, data_type, numeric_precision, numeric_scale, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name = 'mission_outcomes'
ORDER BY ordinal_position;

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = current_schema()
  AND table_name = 'mission_outcomes'
ORDER BY constraint_name;

SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND tablename = 'mission_outcomes'
ORDER BY indexname;

-- EXPIRED 가 허용 목록에 들어갔는지.
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conname = 'ck_mission_member_states_resolution';

-- 집계 무결성: 부분 카운트가 대상 수를 넘거나 비율이 범위를 벗어난 행이 없어야 한다.
SELECT
    COUNT(*) FILTER (WHERE activated_member_count > target_member_count
        OR impressed_member_count > target_member_count)
        AS count_exceeds_target,
    COUNT(*) FILTER (WHERE room_activation_coverage < 0 OR room_activation_coverage > 1
        OR exposure_reach < 0 OR exposure_reach > 1)
        AS ratio_out_of_range,
    COUNT(*) FILTER (WHERE target_member_count = 0
        AND (room_activation_coverage <> 0 OR exposure_reach <> 0))
        AS zero_target_non_zero_ratio
FROM mission_outcomes;

-- 같은 미션·같은 버전이 두 번 확정된 행이 없어야 한다.
SELECT room_mission_id, metric_schema_version, COUNT(*) AS duplicate_count
FROM mission_outcomes
GROUP BY room_mission_id, metric_schema_version
HAVING COUNT(*) > 1;

-- 고아 outcome 이 없어야 한다.
SELECT outcome.id, outcome.room_mission_id
FROM mission_outcomes outcome
LEFT JOIN room_missions mission ON mission.id = outcome.room_mission_id
WHERE mission.id IS NULL;

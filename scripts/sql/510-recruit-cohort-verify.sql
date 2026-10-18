-- #510 up 직후 실행한다. 엔티티와 어긋나거나 제약이 빠졌으면 예외로 멈춘다.
-- 칸 정의는 Hibernate 가 만드는 것과 같아야 한다(새 버전을 먼저 띄운 DB 와 SQL 로 만든 DB 가 같은 모양).
DO $$
DECLARE problems text;
BEGIN
    IF to_regclass('recruit_tracks') IS NULL OR to_regclass('cohorts') IS NULL THEN
        RAISE EXCEPTION 'recruit_tracks or cohorts does not exist';
    END IF;

    -- 칸: 이름·타입·nullable·길이가 모두 같아야 하고, 남는 칸도 없어야 한다.
    WITH expected(tbl, col, data_type, nullable, len) AS (VALUES
        ('recruit_tracks', 'id', 'bigint', 'NO', NULL::int),
        ('recruit_tracks', 'track_key', 'character varying', 'NO', 40),
        ('recruit_tracks', 'label', 'character varying', 'NO', 40),
        ('recruit_tracks', 'region', 'character varying', 'NO', 40),
        ('recruit_tracks', 'audience', 'character varying', 'NO', 20),
        ('recruit_tracks', 'min_age', 'integer', 'NO', NULL),
        ('recruit_tracks', 'max_age', 'integer', 'NO', NULL),
        ('recruit_tracks', 'questions', 'jsonb', 'NO', NULL),
        ('recruit_tracks', 'display_order', 'integer', 'NO', NULL),
        ('recruit_tracks', 'active', 'boolean', 'NO', NULL),
        ('recruit_tracks', 'created_at', 'timestamp without time zone', 'NO', NULL),
        ('recruit_tracks', 'updated_at', 'timestamp without time zone', 'NO', NULL),
        ('recruit_tracks', 'deleted_at', 'timestamp with time zone', 'YES', NULL),
        ('cohorts', 'id', 'bigint', 'NO', NULL),
        ('cohorts', 'track_id', 'bigint', 'NO', NULL),
        ('cohorts', 'sequence', 'integer', 'NO', NULL),
        ('cohorts', 'status', 'character varying', 'NO', 20),
        ('cohorts', 'apply_opens_at', 'timestamp without time zone', 'NO', NULL),
        ('cohorts', 'apply_closes_at', 'timestamp without time zone', 'NO', NULL),
        ('cohorts', 'assign_deadline_at', 'timestamp without time zone', 'NO', NULL),
        ('cohorts', 'period_label', 'character varying', 'NO', 40),
        ('cohorts', 'target_male', 'integer', 'YES', NULL),
        ('cohorts', 'target_female', 'integer', 'YES', NULL),
        ('cohorts', 'settled_at', 'timestamp without time zone', 'YES', NULL),
        ('cohorts', 'created_by', 'character varying', 'YES', 40),
        ('cohorts', 'created_at', 'timestamp without time zone', 'NO', NULL),
        ('cohorts', 'updated_at', 'timestamp without time zone', 'NO', NULL),
        ('cohorts', 'deleted_at', 'timestamp with time zone', 'YES', NULL)
    ), actual AS (
        SELECT table_name::text AS tbl, column_name::text AS col, data_type::text AS data_type,
               is_nullable::text AS nullable, character_maximum_length::int AS len
        FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name IN ('recruit_tracks', 'cohorts')
    ), diff AS (
        (SELECT 'missing or different ' || tbl || '.' || col AS problem FROM (SELECT * FROM expected EXCEPT SELECT * FROM actual) m)
        UNION ALL
        (SELECT 'unexpected ' || tbl || '.' || col FROM (SELECT * FROM actual EXCEPT SELECT * FROM expected) u)
    )
    SELECT string_agg(problem, ', ' ORDER BY problem) INTO problems FROM diff;
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'columns do not match the entities: %', problems;
    END IF;

    -- 제약: 이름만이 아니라 걸린 테이블·종류까지 본다.
    SELECT string_agg(e.tbl || '.' || e.name, ', ') INTO problems
    FROM (VALUES
        ('recruit_tracks', 'uq_recruit_tracks_key', 'u'),
        ('recruit_tracks', 'ck_recruit_tracks_age', 'c'),
        ('recruit_tracks', 'ck_recruit_tracks_audience', 'c'),
        ('recruit_tracks', 'ck_recruit_tracks_questions', 'c'),
        ('cohorts', 'fk_cohorts_track', 'f'),
        ('cohorts', 'uq_cohorts_track_sequence', 'u'),
        ('cohorts', 'ck_cohorts_sequence', 'c'),
        ('cohorts', 'ck_cohorts_status', 'c'),
        ('cohorts', 'ck_cohorts_apply_window', 'c'),
        ('cohorts', 'ck_cohorts_assign_deadline', 'c'),
        ('cohorts', 'ck_cohorts_targets', 'c')
    ) AS e(tbl, name, contype)
    WHERE NOT EXISTS (SELECT 1 FROM pg_constraint
                      WHERE conrelid = e.tbl::regclass AND conname = e.name AND contype::text = e.contype);
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'constraints are missing: %', problems;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'cohorts'::regclass AND conname = 'fk_cohorts_track'
                     AND confrelid = 'recruit_tracks'::regclass) THEN
        RAISE EXCEPTION 'fk_cohorts_track must reference recruit_tracks';
    END IF;

    -- 트랙당 OPEN 1개: track_id 한 칸의 유니크이고, 조건이 정확히 OPEN·미삭제여야 한다.
    IF NOT EXISTS (
        SELECT 1 FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE i.indrelid = 'cohorts'::regclass AND c.relname = 'ux_cohorts_track_open' AND i.indisunique
          AND i.indnatts = 1
          AND (SELECT attname FROM pg_attribute WHERE attrelid = i.indrelid AND attnum = i.indkey[0]) = 'track_id'
          AND regexp_replace(pg_get_expr(i.indpred, i.indrelid), '\s', '', 'g')
              = '(((status)::text=''OPEN''::text)AND(deleted_atISNULL))') THEN
        RAISE EXCEPTION 'ux_cohorts_track_open must be UNIQUE (track_id) WHERE status = ''OPEN'' AND deleted_at IS NULL';
    END IF;
END $$;

SELECT table_name, column_name, data_type, is_nullable, character_maximum_length
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name IN ('recruit_tracks', 'cohorts')
ORDER BY table_name, ordinal_position;

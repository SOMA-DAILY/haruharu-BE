-- #511 up 직후 실행한다. 엔티티와 어긋나거나 제약이 빠졌으면 예외로 멈춘다.
-- 칸 정의는 Hibernate 가 만드는 것과 같아야 한다(새 버전을 먼저 띄운 DB 와 SQL 로 만든 DB 가 같은 모양).
DO $$
DECLARE problems text;
BEGIN
    IF to_regclass('cohort_applications') IS NULL THEN
        RAISE EXCEPTION 'cohort_applications does not exist';
    END IF;

    -- 칸: 이름·타입·nullable·길이가 모두 같아야 하고, 남는 칸도 없어야 한다.
    WITH expected(col, data_type, nullable, len) AS (VALUES
        ('id', 'bigint', 'NO', NULL::int),
        ('user_id', 'bigint', 'NO', NULL),
        ('cohort_id', 'bigint', 'NO', NULL),
        ('track_id', 'bigint', 'NO', NULL),
        ('status', 'character varying', 'NO', 20),
        ('declared_gender', 'character varying', 'YES', 10),
        ('gender', 'character varying', 'YES', 20),
        ('birthday', 'date', 'YES', NULL),
        ('school', 'character varying', 'NO', 40),
        ('department', 'character varying', 'NO', 15),
        ('answers', 'jsonb', 'NO', NULL),
        ('referral_source', 'character varying', 'YES', 40),
        ('friend_preference', 'character varying', 'YES', 20),
        ('motivation', 'character varying', 'YES', 300),
        ('survey_submitted_at', 'timestamp without time zone', 'YES', NULL),
        ('priority', 'integer', 'NO', NULL),
        ('applied_at', 'timestamp without time zone', 'NO', NULL),
        ('queued_at', 'timestamp without time zone', 'YES', NULL),
        ('review_reason', 'character varying', 'YES', 30),
        ('room_id', 'bigint', 'YES', NULL),
        ('assigned_at', 'timestamp without time zone', 'YES', NULL),
        ('assigned_by', 'character varying', 'YES', 40),
        ('closed_at', 'timestamp without time zone', 'YES', NULL),
        ('close_reason', 'character varying', 'YES', 30),
        ('refunded_at', 'timestamp without time zone', 'YES', NULL),
        ('admin_memo', 'character varying', 'YES', 500),
        ('created_at', 'timestamp without time zone', 'NO', NULL),
        ('updated_at', 'timestamp without time zone', 'NO', NULL),
        ('deleted_at', 'timestamp with time zone', 'YES', NULL)
    ), actual AS (
        SELECT column_name::text AS col, data_type::text AS data_type,
               is_nullable::text AS nullable, character_maximum_length::int AS len
        FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'cohort_applications'
    ), diff AS (
        (SELECT 'missing or different ' || col AS problem FROM (SELECT * FROM expected EXCEPT SELECT * FROM actual) m)
        UNION ALL
        (SELECT 'unexpected ' || col FROM (SELECT * FROM actual EXCEPT SELECT * FROM expected) u)
    )
    SELECT string_agg(problem, ', ' ORDER BY problem) INTO problems FROM diff;
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'cohort_applications columns do not match the entity: %', problems;
    END IF;

    -- 제약: 이름만이 아니라 종류까지 본다.
    SELECT string_agg(e.name, ', ') INTO problems
    FROM (VALUES
        ('fk_cohort_applications_cohort', 'f'),
        ('fk_cohort_applications_track', 'f'),
        ('ck_cohort_applications_status', 'c'),
        ('ck_cohort_applications_declared_gender', 'c'),
        ('ck_cohort_applications_friend_preference', 'c'),
        ('ck_cohort_applications_review_reason', 'c'),
        ('ck_cohort_applications_close_reason', 'c'),
        ('ck_cohort_applications_priority', 'c'),
        ('ck_cohort_applications_answers', 'c'),
        ('ck_cohort_applications_closed', 'c')
    ) AS e(name, contype)
    WHERE NOT EXISTS (SELECT 1 FROM pg_constraint
                      WHERE conrelid = 'cohort_applications'::regclass AND conname = e.name AND contype::text = e.contype);
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'cohort_applications constraints are missing: %', problems;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cohort_applications_cohort'
                     AND conrelid = 'cohort_applications'::regclass AND confrelid = 'cohorts'::regclass)
       OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cohort_applications_track'
                     AND conrelid = 'cohort_applications'::regclass AND confrelid = 'recruit_tracks'::regclass) THEN
        RAISE EXCEPTION 'fk_cohort_applications_* must reference cohorts and recruit_tracks';
    END IF;

    -- 진행 중 신청 사람당 1건: user_id 한 칸의 유니크이고, 조건이 정확히 진행 중 4개 상태·미삭제여야 한다.
    IF NOT EXISTS (
        SELECT 1 FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE i.indrelid = 'cohort_applications'::regclass AND c.relname = 'ux_cohort_applications_user_active'
          AND i.indisunique AND i.indnatts = 1
          AND (SELECT attname FROM pg_attribute WHERE attrelid = i.indrelid AND attnum = i.indkey[0]) = 'user_id'
          AND regexp_replace(pg_get_expr(i.indpred, i.indrelid), '\s', '', 'g')
              = '(((status)::text=ANY((ARRAY[''IDENTITY_PENDING''::charactervarying,''NEEDS_REVIEW''::charactervarying,'
                || '''QUEUED''::charactervarying,''ASSIGNED''::charactervarying])::text[]))AND(deleted_atISNULL))') THEN
        RAISE EXCEPTION 'ux_cohort_applications_user_active must be UNIQUE (user_id) WHERE status IN (IDENTITY_PENDING, NEEDS_REVIEW, QUEUED, ASSIGNED) AND deleted_at IS NULL';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE schemaname = current_schema() AND indexname = 'idx_cohort_applications_pool'
          AND indexdef LIKE '%(cohort_id, status, priority DESC, queued_at)') THEN
        RAISE EXCEPTION 'idx_cohort_applications_pool must be (cohort_id, status, priority DESC, queued_at)';
    END IF;

    -- 원장: USE_REFUND 를 받고, 같은 신청의 같은 사유는 한 번만.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'ticket_ledger'::regclass AND conname = 'ticket_ledger_reason_check'
                     AND pg_get_constraintdef(oid) LIKE '%''USE_REFUND''%') THEN
        RAISE EXCEPTION 'ticket_ledger_reason_check must allow USE_REFUND';
    END IF;
    IF (SELECT count(DISTINCT con.oid) FROM pg_constraint con
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
        WHERE con.conrelid = 'ticket_ledger'::regclass AND con.contype = 'c' AND att.attname = 'reason') <> 1 THEN
        RAISE EXCEPTION 'ticket_ledger must have exactly one reason CHECK';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE i.indrelid = 'ticket_ledger'::regclass AND c.relname = 'ux_ticket_ledger_cohort_application_ref'
          AND i.indisunique AND i.indnatts = 3
          AND pg_get_indexdef(i.indexrelid) LIKE '%(ref_type, ref_id, reason)%'
          AND regexp_replace(pg_get_expr(i.indpred, i.indrelid), '\s', '', 'g')
              = '((ref_type)::text=''COHORT_APPLICATION''::text)') THEN
        RAISE EXCEPTION 'ux_ticket_ledger_cohort_application_ref must be UNIQUE (ref_type, ref_id, reason) WHERE ref_type = ''COHORT_APPLICATION''';
    END IF;

    -- 프로필 번호
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema() AND table_name = 'user_profiles' AND column_name = 'phone'
                     AND data_type = 'character varying' AND character_maximum_length = 20 AND is_nullable = 'YES') THEN
        RAISE EXCEPTION 'user_profiles.phone must be nullable varchar(20)';
    END IF;
END $$;

SELECT column_name, data_type, is_nullable, character_maximum_length
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'cohort_applications'
ORDER BY ordinal_position;

-- #513 up 직후 실행한다. 엔티티와 어긋나거나 제약이 빠졌으면 예외로 멈춘다.
-- 칸 정의는 Hibernate 가 만드는 것과 같아야 한다(새 버전을 먼저 띄운 DB 와 SQL 로 만든 DB 가 같은 모양).
DO $$
DECLARE problems text;
BEGIN
    -- 칸: 이름·타입·nullable·길이가 모두 같아야 한다(두 테이블의 다른 칸은 이 이슈 범위가 아니라 보지 않는다).
    WITH expected(tbl, col, data_type, nullable, len) AS (VALUES
        ('rooms', 'cohort_id', 'bigint', 'YES', NULL::int),
        ('rooms', 'name', 'character varying', 'YES', 50),
        ('rooms', 'male_capacity', 'integer', 'YES', NULL),
        ('rooms', 'female_capacity', 'integer', 'YES', NULL),
        ('room_members', 'entry_path', 'character varying', 'NO', 20),
        ('room_members', 'application_id', 'bigint', 'YES', NULL)
    ), actual AS (
        SELECT table_name::text AS tbl, column_name::text AS col, data_type::text AS data_type,
               is_nullable::text AS nullable, character_maximum_length::int AS len
        FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name IN ('rooms', 'room_members')
    )
    SELECT string_agg(tbl || '.' || col, ', ' ORDER BY tbl, col) INTO problems
    FROM (SELECT * FROM expected EXCEPT SELECT * FROM actual) m;
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'columns missing or different from the entity: %', problems;
    END IF;

    -- 기존 행 백필: 기본값은 INVITE 이고 비어 있는 행이 없다.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema() AND table_name = 'room_members' AND column_name = 'entry_path'
                     AND column_default LIKE '''INVITE''%') THEN
        RAISE EXCEPTION 'room_members.entry_path must default to INVITE';
    END IF;

    -- 참조: 이름만 보지 않고 양쪽 칸·검증 완료(NOT VALID 아님)·삭제/갱신 동작(NO ACTION)까지 본다.
    SELECT string_agg(e.name, ', ') INTO problems
    FROM (VALUES
        ('fk_rooms_cohort', 'rooms', 'cohort_id', 'cohorts', 'id'),
        ('fk_room_members_application', 'room_members', 'application_id', 'cohort_applications', 'id')
    ) AS e(name, tbl, col, ref_tbl, ref_col)
    WHERE NOT EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conname = e.name AND c.contype = 'f'
          AND c.conrelid = e.tbl::regclass AND c.confrelid = e.ref_tbl::regclass
          AND c.conkey = ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid = e.tbl::regclass AND attname = e.col)]
          AND c.confkey = ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid = e.ref_tbl::regclass AND attname = e.ref_col)]
          AND c.convalidated AND c.confdeltype = 'a' AND c.confupdtype = 'a');
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'foreign keys missing or different (columns, validated, NO ACTION): %', problems;
    END IF;

    -- CHECK 는 두지 않는다(규칙은 도메인이 지킨다). Hibernate 가 붙인 이름 없는 CHECK 도 남아 있으면 안 된다.
    SELECT string_agg(DISTINCT con.conname, ', ') INTO problems FROM pg_constraint con
    JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
    WHERE con.contype = 'c'
      AND ((con.conrelid = 'room_members'::regclass AND att.attname IN ('entry_path', 'application_id'))
        OR (con.conrelid = 'rooms'::regclass AND att.attname IN ('cohort_id', 'name', 'male_capacity', 'female_capacity')));
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'CHECK constraints must not exist on #513 columns: %', problems;
    END IF;

    -- 한 신청은 살아 있는 멤버 하나: application_id 한 칸의 유니크이고 조건이 정확히 신청 있음·미삭제여야 한다.
    IF NOT EXISTS (
        SELECT 1 FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE i.indrelid = 'room_members'::regclass AND c.relname = 'ux_room_members_application'
          AND i.indisunique AND i.indisvalid AND i.indnatts = 1
          AND (SELECT attname FROM pg_attribute WHERE attrelid = i.indrelid AND attnum = i.indkey[0]) = 'application_id'
          AND regexp_replace(pg_get_expr(i.indpred, i.indrelid), '\s', '', 'g')
              = '((application_idISNOTNULL)AND(deleted_atISNULL))') THEN
        RAISE EXCEPTION 'ux_room_members_application must be UNIQUE (application_id) WHERE application_id IS NOT NULL AND deleted_at IS NULL';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE i.indrelid = 'rooms'::regclass AND c.relname = 'idx_rooms_cohort'
          AND i.indisvalid AND i.indnatts = 1 AND i.indpred IS NULL
          AND (SELECT attname FROM pg_attribute WHERE attrelid = i.indrelid AND attnum = i.indkey[0]) = 'cohort_id') THEN
        RAISE EXCEPTION 'idx_rooms_cohort must be a valid index on rooms (cohort_id)';
    END IF;

    -- 데이터 감사: CHECK 대신 도메인 규칙(RoomEntryPath·RoomMember 짝·RoomComposition 정원)에 어긋난 살아 있는 행이 있으면 멈춘다.
    -- 운영 중 직접 SQL 로 이 칸을 고친 뒤에도 이 스크립트를 다시 돌려 확인한다. 알 수 없는 entry_path 는 엔티티 로딩부터 실패한다.
    SELECT string_agg(problem, ', ') INTO problems FROM (
        SELECT 'room_members.entry_path unknown: ' || count(*) AS problem FROM room_members
        WHERE deleted_at IS NULL AND entry_path NOT IN ('INVITE', 'APPLICATION') HAVING count(*) > 0
        UNION ALL
        SELECT 'room_members entry_path/application_id mismatch: ' || count(*) FROM room_members
        WHERE deleted_at IS NULL AND (entry_path = 'APPLICATION') <> (application_id IS NOT NULL) HAVING count(*) > 0
        UNION ALL
        SELECT 'rooms capacity below 1: ' || count(*) FROM rooms
        WHERE deleted_at IS NULL AND (male_capacity < 1 OR female_capacity < 1) HAVING count(*) > 0
    ) p;
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'rows break the domain rules (fix them before deploy): %', problems;
    END IF;
END $$;

SELECT entry_path, count(*) AS members FROM room_members GROUP BY entry_path ORDER BY entry_path;

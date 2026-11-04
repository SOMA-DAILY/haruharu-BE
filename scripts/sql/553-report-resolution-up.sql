-- #553: widen only the reports.status value CHECK so SANCTIONED / REJECTED are accepted.
-- Keeps constraint names, existing values and unrelated constraints. Apply before the admin binary that resolves reports.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $$
DECLARE
    existing RECORD;
    value_check_found BOOLEAN := FALSE;
    allowed BOOLEAN;
    candidate TEXT;
BEGIN
    IF to_regclass('reports') IS NULL THEN
        RAISE EXCEPTION 'reports does not exist; create the existing report schema first';
    END IF;
    -- 처리자·처리 시각(#553). prod 태스크는 ddl-auto=update 라 자동으로도 생긴다. 멱등.
    ALTER TABLE reports ADD COLUMN IF NOT EXISTS resolved_by_admin_id BIGINT;
    ALTER TABLE reports ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP(6);
    ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_status_rollback_check;
    FOR existing IN
        SELECT c.conname, pg_get_expr(c.conbin, c.conrelid) AS expression
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'status'
        WHERE c.conrelid = 'reports'::regclass AND c.contype = 'c'
          AND c.conkey = ARRAY[a.attnum]
    LOOP
        IF existing.expression ~ '''RECEIVED''' THEN
            value_check_found := TRUE;
            IF strpos(existing.expression, '''SANCTIONED''') = 0 OR strpos(existing.expression, '''REJECTED''') = 0 THEN
                EXECUTE format('ALTER TABLE reports DROP CONSTRAINT %I', existing.conname);
                EXECUTE format('ALTER TABLE reports ADD CONSTRAINT %I CHECK ((%s) OR status IN (''SANCTIONED'', ''REJECTED''))',
                    existing.conname, existing.expression);
            END IF;
        ELSIF existing.expression ~ '\m(ANY|IN)\M' THEN
            RAISE EXCEPTION 'Unrecognized report status value CHECK %; review it before migration', existing.conname;
        END IF;
    END LOOP;
    IF NOT value_check_found THEN
        ALTER TABLE reports ADD CONSTRAINT reports_status_values_check CHECK
            (status IN ('RECEIVED', 'SANCTIONED', 'REJECTED'));
    END IF;
    FOREACH candidate IN ARRAY ARRAY['SANCTIONED', 'REJECTED'] LOOP
        FOR existing IN
            SELECT c.conname, pg_get_expr(c.conbin, c.conrelid) AS expression
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'status'
            WHERE c.conrelid = 'reports'::regclass AND c.contype = 'c'
              AND c.conkey = ARRAY[a.attnum]
        LOOP
            EXECUTE format('SELECT (%s) FROM (SELECT %L::varchar AS status) candidate', existing.expression, candidate)
                INTO allowed;
            IF allowed IS FALSE THEN
                RAISE EXCEPTION 'Report status CHECK % still rejects %; manual review required', existing.conname, candidate;
            END IF;
        END LOOP;
    END LOOP;
END $$;
COMMIT;

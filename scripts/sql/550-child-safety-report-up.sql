-- #550: widen only report-reason value CHECKs; keep their names, existing values and unrelated constraints.
-- Apply before app/admin binaries that know CHILD_SAFETY and before the Android update.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $$
DECLARE
    existing RECORD;
    value_check_found BOOLEAN := FALSE;
    child_safety_allowed BOOLEAN;
BEGIN
    IF to_regclass('reports') IS NULL THEN
        RAISE EXCEPTION 'reports does not exist; create the existing report schema first';
    END IF;
    ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_child_safety_rollback_check;
    FOR existing IN
        SELECT c.conname, pg_get_expr(c.conbin, c.conrelid) AS expression
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'reason'
        WHERE c.conrelid = 'reports'::regclass AND c.contype = 'c'
          AND c.conkey = ARRAY[a.attnum]
    LOOP
        IF existing.expression ~ '''(INAPPROPRIATE_MEDIA|ABUSIVE_LANGUAGE|FAKE_PROFILE|SPAM_AD|OTHER)''' THEN
            value_check_found := TRUE;
            IF strpos(existing.expression, '''CHILD_SAFETY''') = 0 THEN
                EXECUTE format('ALTER TABLE reports DROP CONSTRAINT %I', existing.conname);
                EXECUTE format('ALTER TABLE reports ADD CONSTRAINT %I CHECK ((%s) OR reason = ''CHILD_SAFETY'')',
                    existing.conname, existing.expression);
            END IF;
        ELSIF existing.expression ~ '\m(ANY|IN)\M' THEN
            RAISE EXCEPTION 'Unrecognized report reason value CHECK %; review it before migration', existing.conname;
        END IF;
    END LOOP;
    IF NOT value_check_found THEN
        ALTER TABLE reports ADD CONSTRAINT reports_reason_values_check CHECK
            (reason IN ('CHILD_SAFETY', 'INAPPROPRIATE_MEDIA', 'ABUSIVE_LANGUAGE', 'FAKE_PROFILE', 'SPAM_AD', 'OTHER'));
    END IF;
    -- Fail atomically if a custom reason-only CHECK would still reject the new value.
    FOR existing IN
        SELECT c.conname, pg_get_expr(c.conbin, c.conrelid) AS expression
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'reason'
        WHERE c.conrelid = 'reports'::regclass AND c.contype = 'c'
          AND c.conkey = ARRAY[a.attnum]
    LOOP
        EXECUTE format('SELECT (%s) FROM (SELECT ''CHILD_SAFETY''::varchar AS reason) candidate', existing.expression)
            INTO child_safety_allowed;
        IF child_safety_allowed IS FALSE THEN
            RAISE EXCEPTION 'Report reason CHECK % still rejects CHILD_SAFETY; manual review required', existing.conname;
        END IF;
    END LOOP;
END $$;
COMMIT;

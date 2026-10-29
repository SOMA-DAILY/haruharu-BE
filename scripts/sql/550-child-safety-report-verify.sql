-- Read-only metadata validation: does not insert/update a report or expose report text or participant IDs.
DO $$
DECLARE
    existing RECORD;
    value_check_found BOOLEAN := FALSE;
    child_safety_allowed BOOLEAN;
BEGIN
    FOR existing IN
        SELECT c.conname, pg_get_expr(c.conbin, c.conrelid) AS expression
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'reason'
        WHERE c.conrelid = 'reports'::regclass AND c.contype = 'c'
          AND c.conkey = ARRAY[a.attnum]
    LOOP
        IF existing.expression ~ '''(INAPPROPRIATE_MEDIA|ABUSIVE_LANGUAGE|FAKE_PROFILE|SPAM_AD|OTHER)''' THEN
            value_check_found := TRUE;
        END IF;
        EXECUTE format('SELECT (%s) FROM (SELECT ''CHILD_SAFETY''::varchar AS reason) candidate', existing.expression)
            INTO child_safety_allowed;
        IF child_safety_allowed IS FALSE THEN
            RAISE EXCEPTION 'Report reason CHECK % rejects CHILD_SAFETY; run 550-child-safety-report-up.sql', existing.conname;
        END IF;
    END LOOP;
    IF NOT value_check_found THEN
        RAISE EXCEPTION 'Report reason value CHECK is missing; run 550-child-safety-report-up.sql';
    END IF;
END $$;
SELECT conname, pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'reason'
WHERE c.conrelid = 'reports'::regclass AND c.contype = 'c' AND c.conkey = ARRAY[a.attnum]
ORDER BY conname;

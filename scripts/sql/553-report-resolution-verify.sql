-- Read-only: confirms SANCTIONED / REJECTED pass every reports.status CHECK. No report rows are read.
DO $$
DECLARE
    existing RECORD;
    allowed BOOLEAN;
    candidate TEXT;
BEGIN
    FOREACH candidate IN ARRAY ARRAY['SANCTIONED', 'REJECTED'] LOOP
        FOR existing IN
            SELECT c.conname, pg_get_expr(c.conbin, c.conrelid) AS expression
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'status'
            WHERE c.conrelid = 'reports'::regclass AND c.contype = 'c' AND c.conkey = ARRAY[a.attnum]
        LOOP
            EXECUTE format('SELECT (%s) FROM (SELECT %L::varchar AS status) candidate', existing.expression, candidate)
                INTO allowed;
            IF allowed IS FALSE THEN
                RAISE EXCEPTION 'Report status CHECK % rejects %; run 553-report-resolution-up.sql', existing.conname, candidate;
            END IF;
        END LOOP;
    END LOOP;
END $$;
SELECT conname, pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'status'
WHERE c.conrelid = 'reports'::regclass AND c.contype = 'c' AND c.conkey = ARRAY[a.attnum]
ORDER BY conname;

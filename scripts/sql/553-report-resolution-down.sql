-- Rollback for #553: fails if any resolved report exists; otherwise forbids the new values again without touching other CHECKs.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM reports WHERE status IN ('SANCTIONED', 'REJECTED')) THEN
        RAISE EXCEPTION 'resolved reports exist; keep a server that understands SANCTIONED/REJECTED';
    END IF;
    ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_status_rollback_check;
    ALTER TABLE reports ADD CONSTRAINT reports_status_rollback_check CHECK (status NOT IN ('SANCTIONED', 'REJECTED'));
END $$;
COMMIT;

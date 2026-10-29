-- #550 rollback never deletes/reclassifies reports and preserves unknown pre-existing allowed reasons.
-- Roll back clients first. Do not roll back the server once CHILD_SAFETY reports have been received.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM reports WHERE reason = 'CHILD_SAFETY') THEN
        RAISE EXCEPTION 'CHILD_SAFETY reports exist; keep the compatible server and reconcile manually';
    END IF;
END $$;
ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_child_safety_rollback_check;
ALTER TABLE reports ADD CONSTRAINT reports_child_safety_rollback_check CHECK (reason <> 'CHILD_SAFETY');
COMMIT;

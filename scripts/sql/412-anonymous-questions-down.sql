BEGIN;
-- Rollback is deliberately data-preserving. Stop if any questions, grants or content reports exist;
-- export/resolve those records explicitly before attempting a destructive rollback.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM anonymous_questions) OR EXISTS (SELECT 1 FROM question_grants)
       OR EXISTS (SELECT 1 FROM reports WHERE question_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Question records exist; rollback requires an explicit retention/export decision';
    END IF;
END $$;
ALTER TABLE reports DROP CONSTRAINT uq_reports_reporter_reported;
ALTER TABLE reports ADD CONSTRAINT uq_reports_reporter_reported UNIQUE (reporter_user_id, reported_user_id);
DROP INDEX IF EXISTS idx_reports_question;
ALTER TABLE reports DROP COLUMN report_scope;
ALTER TABLE reports DROP COLUMN question_id;
DROP TABLE question_grants;
DROP TABLE anonymous_questions;
-- The expanded notification enum CHECK remains backward compatible and preserves notification history.
COMMIT;

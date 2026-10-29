-- Read-only preflight. Inspect existing values and CHECK/unique definitions before running up.
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'reports'
  AND column_name IN ('reason', 'reported_user_id', 'report_scope', 'question_id')
ORDER BY ordinal_position;

SELECT conname, contype, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'reports'::regclass AND contype IN ('c', 'u')
ORDER BY conname;

-- Aggregate reason counts only: no report detail, participant identifier or contact information.
SELECT reason, count(*) AS report_count
FROM reports
GROUP BY reason
ORDER BY reason;

-- #553: reports.status 에 SANCTIONED / REJECTED 허용 + 처리자, 처리 시각. 멱등. 신고 처리 서버 배포 전에 적용.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE reports ADD COLUMN IF NOT EXISTS resolved_by_admin_id BIGINT;
ALTER TABLE reports ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP(6);
ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_status_check;
ALTER TABLE reports ADD CONSTRAINT reports_status_check CHECK (status IN ('RECEIVED', 'SANCTIONED', 'REJECTED'));
COMMIT;

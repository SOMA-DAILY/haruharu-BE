-- #554 롤백. 접속기록은 법정 보관 대상이라 운영에서는 실행하지 말 것 — dev 되돌리기용.
BEGIN;
DROP TABLE IF EXISTS admin_audit_reviews;
DROP TABLE IF EXISTS admin_access_logs;
COMMIT;

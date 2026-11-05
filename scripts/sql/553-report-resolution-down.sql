-- #553 되돌리기: status CHECK 를 RECEIVED 만으로 되돌린다. 처리된 신고가 있으면 ADD CONSTRAINT 가 실패해 그대로 롤백된다.
-- 처리자 칸 2개는 남긴다(옛 서버는 안 읽음).
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_status_check;
ALTER TABLE reports ADD CONSTRAINT reports_status_check CHECK (status IN ('RECEIVED'));
COMMIT;

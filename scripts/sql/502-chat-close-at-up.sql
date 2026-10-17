-- #502. 구버전과 호환되는 nullable 컬럼만 먼저 추가한다. psql -v ON_ERROR_STOP=1 권장.
-- 새 코드 기동 전 backfill / verify까지 완료해야 한다. 운영 적용은 별도 승인 대상이다.
BEGIN;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE chat_rooms ADD COLUMN IF NOT EXISTS close_at timestamp without time zone;
COMMENT ON COLUMN chat_rooms.close_at IS 'GROUP 실시간 채팅 종료 확정 시각. NULL은 열림. 예정 종료 시각이 아님.';
COMMIT;

-- 구버전 app/admin의 종료·취소·채팅 쓰기를 멈춘 최종 전환 구간에서 실행한다.
-- 한 번에 최대 1,000행. UPDATE 0이 나올 때까지 반복하고 verify를 실행한다.
-- 과거 실제 종료 시각을 추정하지 않고 보정 확정 시각을 기록한다. 기존 값은 보존한다.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Seoul';
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '30s';
WITH targets AS (
    SELECT c.id
    FROM chat_rooms c LEFT JOIN rooms r ON r.id = c.room_id
    WHERE c.type = 'GROUP' AND c.deleted_at IS NULL AND c.close_at IS NULL
      AND (r.id IS NULL OR r.deleted_at IS NOT NULL OR r.status IN ('ENDED', 'CANCELLED'))
    ORDER BY c.id
    LIMIT 1000
)
UPDATE chat_rooms c SET close_at = localtimestamp, updated_at = localtimestamp
FROM targets t WHERE c.id = t.id AND c.close_at IS NULL;
COMMIT;

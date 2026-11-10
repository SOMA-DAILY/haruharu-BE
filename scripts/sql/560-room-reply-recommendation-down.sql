-- #560 되돌리기: 방 단위 추천 스위치 칼럼을 지운다. ddl-auto=validate 이므로 이 칼럼을 쓰는 앱을 먼저 이전 버전으로 되돌린다.
-- 되돌린 앱은 REPLY_RECOMMENDATION_ROOM_IDS 만 본다(비어 있으면 모든 방).
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE rooms DROP COLUMN IF EXISTS reply_recommendation_enabled;
COMMIT;

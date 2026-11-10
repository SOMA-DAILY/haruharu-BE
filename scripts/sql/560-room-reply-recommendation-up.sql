-- #560: 방 단위 영상 답장 추천 스위치. 앱의 추천 기능은
-- 전체 스위치(REPLY_RECOMMENDATION_ENABLED) AND (이 칸이 true OR REPLY_RECOMMENDATION_ROOM_IDS 에 포함) 일 때만 켜진다.
-- 기존 방은 false 로 채워진다(DEFAULT 상수라 표를 다시 쓰지 않는다). 멱등이다.
-- 앱은 ddl-auto=validate 이므로 이 칼럼을 쓰는 앱을 배포하기 전에 먼저 적용한다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS reply_recommendation_enabled BOOLEAN NOT NULL DEFAULT false;
COMMIT;

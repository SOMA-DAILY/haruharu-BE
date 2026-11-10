-- #559 되돌리기: 영상 답장 Depth 라벨 표를 지운다. 스위치(haruharu.reply-recommendation.depth.enabled)를 먼저 끈다.
-- 앱의 수집기·탈퇴 파기는 표가 없으면 건너뛰므로 앱을 되돌리지 않아도 된다. 558 표는 건드리지 않는다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
DROP TABLE IF EXISTS ai_reply.reply_depth_observation;
COMMIT;

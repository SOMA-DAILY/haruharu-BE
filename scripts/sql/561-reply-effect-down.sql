-- #560(이슈) 되돌리기: 비교 그룹 비율(REPLY_RECOMMENDATION_HOLDOUT_RATIO)을 먼저 0 으로 둔다.
-- 앱은 칸·표가 없으면 효과 라벨과 그룹 기록을 건너뛰므로 앱을 되돌리지 않아도 된다. 다만 칸이 있다고 확인한 뒤에는 다시 묻지 않으므로
-- 이 파일을 적용한 뒤 앱을 다시 시작한다. 558·559 칸은 건드리지 않는다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
DROP TABLE IF EXISTS ai_reply.holdout_assignment;
ALTER TABLE ai_reply.reply_link DROP COLUMN IF EXISTS experiment_arm;
ALTER TABLE ai_reply.reply_depth_observation DROP COLUMN IF EXISTS bystanders_responded;
ALTER TABLE ai_reply.reply_depth_observation DROP COLUMN IF EXISTS bystander_members;
ALTER TABLE ai_reply.reply_depth_observation DROP COLUMN IF EXISTS response_window_minutes;
ALTER TABLE ai_reply.reply_depth_observation DROP COLUMN IF EXISTS owner_response_minutes;
ALTER TABLE ai_reply.reply_depth_observation DROP COLUMN IF EXISTS owner_responded;
COMMIT;

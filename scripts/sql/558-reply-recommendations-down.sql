-- #558 되돌리기: 영상 답장 추천 AI 스키마(ai_reply)를 지운다.
-- 기능 스위치(haruharu.reply-recommendation.enabled)와 AI 작업기를 먼저 끈 뒤 실행한다. 추천·사건·판정 기록이 모두 사라진다.
-- video_caption_revisions 는 지우지 않는다: 앱 코드(VideoService·탈퇴 파기)가 캡션 수정 때마다 쓰고, 운영은 시작할 때
-- 스키마를 검증(ddl-auto validate)하므로 표가 없으면 앱이 뜨지 않는다. #558 앱 코드를 되돌려 배포한 뒤에만
--   DROP TABLE IF EXISTS video_caption_revisions;
-- 를 따로 실행한다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
DROP SCHEMA IF EXISTS ai_reply CASCADE;
COMMIT;

-- #445 업로드 세션 스키마를 반영한 뒤 실행한다.
BEGIN;
ALTER TABLE video_posts ALTER COLUMN caption TYPE VARCHAR(100);
ALTER TABLE video_upload_sessions ALTER COLUMN caption TYPE VARCHAR(100);
COMMIT;

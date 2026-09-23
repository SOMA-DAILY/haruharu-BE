-- 배포 전 수동 적용. 기존 게시물은 feed_video_url=NULL → 기존 video_url 사용.
BEGIN;
ALTER TABLE video_posts ADD COLUMN IF NOT EXISTS feed_video_url varchar(500);
CREATE TABLE IF NOT EXISTS video_upload_sessions (
 id uuid PRIMARY KEY,
 user_id bigint NOT NULL,
 room_id bigint NOT NULL,
 member_id bigint NOT NULL REFERENCES room_members(id),
 slot_start timestamp NOT NULL,
 expires_at timestamp NOT NULL,
 caption varchar(20),
 post_id bigint REFERENCES video_posts(id),
 cancelled boolean NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_video_upload_member_slot ON video_upload_sessions(member_id,slot_start);
COMMIT;
-- 확인: SELECT column_name FROM information_schema.columns WHERE table_name='video_upload_sessions';
-- S3: users/video-upload-staging/*/videos/*는 임시 파일. users/*/videos/*/published/*는 PUT URL을 발급하지 않음.
-- 배포 시 scripts/s3/video-staging-lifecycle.sh로 users/video-upload-staging/ 객체 3일 만료 규칙을 적용·확인한다.
-- 게시 완료 세션 DB 행은 멱등 재시도를 위해 보존하며, staging S3 원본은 게시 후 사용하지 않는다.

-- #548 중간 참여. 원본을 지우지 않는 추가 스키마. 앱/관리자 배포 전에 실행한다.
BEGIN;
SET LOCAL lock_timeout = '5s';
CREATE TABLE IF NOT EXISTS mid_participation_preferences (
  user_id bigint PRIMARY KEY, enabled boolean NOT NULL,
  created_at timestamp NOT NULL DEFAULT localtimestamp, updated_at timestamp NOT NULL DEFAULT localtimestamp,
  deleted_at timestamptz
);
CREATE TABLE IF NOT EXISTS mid_participation_campaigns (
  id bigserial PRIMARY KEY, room_id bigint NOT NULL, seats integer NOT NULL, accepted_count integer NOT NULL DEFAULT 0,
  expires_at timestamp NOT NULL, cancelled_at timestamp,
  created_at timestamp NOT NULL DEFAULT localtimestamp, updated_at timestamp NOT NULL DEFAULT localtimestamp,
  deleted_at timestamptz
);
CREATE TABLE IF NOT EXISTS mid_participation_invitations (
  id bigserial PRIMARY KEY, user_id bigint NOT NULL, campaign_id bigint, kind varchar(20) NOT NULL,
  delivery_version integer NOT NULL DEFAULT 1, seen_version integer NOT NULL DEFAULT 0,
  declined_at timestamp, accepted_at timestamp, accepted_member_id bigint, delivered_at timestamp NOT NULL,
  member_removed_at timestamp, notified_version integer NOT NULL DEFAULT 0,
  notification_claim_version integer NOT NULL DEFAULT 0, notification_claimed_at timestamp,
  created_at timestamp NOT NULL DEFAULT localtimestamp, updated_at timestamp NOT NULL DEFAULT localtimestamp,
  deleted_at timestamptz,
  CONSTRAINT uk_mid_invitation_recipient UNIQUE(campaign_id,user_id)
);
CREATE TABLE IF NOT EXISTS room_member_removals (
  member_id bigint PRIMARY KEY, room_id bigint NOT NULL, user_id bigint NOT NULL, removed_at timestamp NOT NULL,
  reason varchar(200), admin_id bigint,
  created_at timestamp NOT NULL DEFAULT localtimestamp, updated_at timestamp NOT NULL DEFAULT localtimestamp,
  deleted_at timestamptz,
  CONSTRAINT uk_room_removed_user UNIQUE(room_id,user_id)
);
ALTER TABLE mid_participation_invitations ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE mid_participation_invitations ADD COLUMN IF NOT EXISTS notified_version integer NOT NULL DEFAULT 0;
ALTER TABLE mid_participation_invitations ADD COLUMN IF NOT EXISTS notification_claim_version integer NOT NULL DEFAULT 0;
ALTER TABLE mid_participation_invitations ADD COLUMN IF NOT EXISTS notification_claimed_at timestamp;
ALTER TABLE room_members ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE video_posts ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE video_upload_sessions ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE video_reactions ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE chat_rooms ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE chat_room_members ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS reference_suppressed boolean NOT NULL DEFAULT false;
ALTER TABLE chat_message_reactions ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE anonymous_questions ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE final_selections ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
ALTER TABLE notification_history ADD COLUMN IF NOT EXISTS member_removed_at timestamp;
-- entry_path 칸은 #513 부터 CHECK 를 두지 않는다. ddl-auto 가 다시 만든 경우만 걷어낸다.
ALTER TABLE room_members DROP CONSTRAINT IF EXISTS room_members_entry_path_check;
ALTER TABLE room_members DROP CONSTRAINT IF EXISTS ck_room_members_entry_path;
-- 알림 타입 CHECK 는 새 값을 넣어 다시 만든다(#540 과 같은 방식).
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS notification_history_type_check;
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS ck_notification_history_type;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check
    CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'VIDEO_REPLY', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'QUESTION_ANSWER_DEADLINE_SOON', 'QUESTION_SEND_DEADLINE_SOON', 'COIN_REFUNDED', 'PAYMENT_COMPLETED', 'WINBACK', 'ADMIN_MESSAGE', 'PROFILE_REVEAL', 'CHAT_REPLY', 'PAIR_CHAT', 'MID_PARTICIPATION_INTEREST', 'MID_PARTICIPATION_OFFER'));
CREATE INDEX IF NOT EXISTS ix_mid_campaign_room ON mid_participation_campaigns(room_id,id);
CREATE INDEX IF NOT EXISTS ix_mid_invitation_user ON mid_participation_invitations(user_id,id);
CREATE INDEX IF NOT EXISTS ix_mid_invitation_campaign ON mid_participation_invitations(campaign_id,id);
CREATE INDEX IF NOT EXISTS ix_mid_notification_pending ON mid_participation_invitations(delivered_at,id)
 WHERE member_removed_at IS NULL AND deleted_at IS NULL AND notified_version<delivery_version;
CREATE INDEX IF NOT EXISTS ix_nh_created_at ON notification_history(created_at);
CREATE INDEX IF NOT EXISTS ix_room_removed_user ON room_member_removals(room_id,user_id);
CREATE INDEX IF NOT EXISTS ix_participation_chat_daily ON chat_messages(chat_room_id,sender_id,created_at)
 WHERE deleted_at IS NULL AND admin_deleted_at IS NULL AND sender_type='USER';
CREATE INDEX IF NOT EXISTS ix_participation_question_daily ON anonymous_questions(room_id,sender_user_id,created_at)
 WHERE deleted_at IS NULL;
COMMIT;

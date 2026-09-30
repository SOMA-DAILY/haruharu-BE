BEGIN;
-- 채팅 P0 기반(#455). 방 참가 전 온보딩 프로필 3칸(전부 null 허용).
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS birth_year integer;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS department varchar(15);
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS into_now varchar(40);
-- 알림 타입 3종 추가(439 판 목록 + ADMIN_MESSAGE, PROFILE_REVEAL, CHAT_REPLY).
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS notification_history_type_check;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'VIDEO_REPLY', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'QUESTION_ANSWER_DEADLINE_SOON', 'QUESTION_SEND_DEADLINE_SOON', 'COIN_REFUNDED', 'MATCH_CONSOLATION', 'PAYMENT_COMPLETED', 'WINBACK', 'ADMIN_MESSAGE', 'PROFILE_REVEAL', 'CHAT_REPLY'));
-- 운영 전용 시스템 유저(닉네임 '하루'). 로그인 수단(user_credentials)이 없는 '하루' 프로필이 이미 있으면 다시 만들지 않는다.
-- 만든 id 는 verify.sql 로 확인해 env CHAT_SYSTEM_USER_ID 로 넣는다.
DO $$
DECLARE new_user_id bigint;
BEGIN
  IF NOT EXISTS (
      SELECT 1 FROM user_profiles p
      WHERE p.nickname = '하루' AND p.deleted_at IS NULL
        AND NOT EXISTS (SELECT 1 FROM user_credentials c WHERE c.user_id = p.user_id)) THEN
    INSERT INTO users (status, created_at, updated_at) VALUES ('ACTIVE', now(), now()) RETURNING id INTO new_user_id;
    INSERT INTO user_profiles (user_id, nickname, created_at, updated_at) VALUES (new_user_id, '하루', now(), now());
    RAISE NOTICE 'chat system user id = %', new_user_id;
  END IF;
END $$;
COMMIT;

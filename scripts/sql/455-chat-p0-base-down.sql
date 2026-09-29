BEGIN;
DO $$ BEGIN IF EXISTS (SELECT 1 FROM notification_history WHERE type IN ('ADMIN_MESSAGE', 'PROFILE_REVEAL', 'CHAT_REPLY')) THEN RAISE EXCEPTION 'Chat P0 notification history exists; archive it before rollback'; END IF; END $$;
DO $$ BEGIN IF EXISTS (SELECT 1 FROM user_profiles WHERE birth_year IS NOT NULL OR department IS NOT NULL OR into_now IS NOT NULL) THEN RAISE EXCEPTION 'Room profile data exists; archive it before rollback'; END IF; END $$;
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS notification_history_type_check;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'VIDEO_REPLY', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'QUESTION_ANSWER_DEADLINE_SOON', 'QUESTION_SEND_DEADLINE_SOON', 'COIN_REFUNDED', 'MATCH_CONSOLATION', 'PAYMENT_COMPLETED', 'WINBACK'));
ALTER TABLE user_profiles DROP COLUMN IF EXISTS into_now;
ALTER TABLE user_profiles DROP COLUMN IF EXISTS department;
ALTER TABLE user_profiles DROP COLUMN IF EXISTS birth_year;
-- 시스템 유저는 보낸 채팅이 없을 때만 지운다(운영 글이 남아 있으면 sender_id 가 끊기므로 남겨둔다).
DO $$
DECLARE system_ids bigint[];
BEGIN
  SELECT array_agg(p.user_id) INTO system_ids FROM user_profiles p
  WHERE p.nickname = '하루'
    AND NOT EXISTS (SELECT 1 FROM user_credentials c WHERE c.user_id = p.user_id)
    AND NOT EXISTS (SELECT 1 FROM chat_messages m WHERE m.sender_id = p.user_id)
    AND NOT EXISTS (SELECT 1 FROM room_members r WHERE r.user_id = p.user_id);
  IF system_ids IS NOT NULL THEN
    DELETE FROM user_profiles WHERE user_id = ANY(system_ids);
    DELETE FROM users WHERE id = ANY(system_ids);
  END IF;
END $$;
COMMIT;

-- #548 되돌리기. 중간 참여/퇴장 데이터가 하나라도 있으면 멈춘다.
-- 행을 지우지 않는다. 데이터가 있으면 보관/정리 방법을 먼저 정한다.
BEGIN;
SET LOCAL lock_timeout = '5s';
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM mid_participation_campaigns)
            OR EXISTS (SELECT 1 FROM mid_participation_invitations)
            OR EXISTS (SELECT 1 FROM mid_participation_preferences)
            OR EXISTS (SELECT 1 FROM room_member_removals)
            OR EXISTS (SELECT 1 FROM room_members WHERE entry_path = 'MID_PARTICIPATION')
            OR EXISTS (SELECT 1 FROM notification_history WHERE type IN ('MID_PARTICIPATION_INTEREST', 'MID_PARTICIPATION_OFFER')) THEN
        RAISE EXCEPTION 'Mid participation data exists; manual reconciliation required';
    END IF;
END $$;
DROP INDEX IF EXISTS ix_nh_created_at;
DROP INDEX IF EXISTS ix_participation_chat_daily;
DROP INDEX IF EXISTS ix_participation_question_daily;
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS notification_history_type_check;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check
    CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'VIDEO_REPLY', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'QUESTION_ANSWER_DEADLINE_SOON', 'QUESTION_SEND_DEADLINE_SOON', 'COIN_REFUNDED', 'PAYMENT_COMPLETED', 'WINBACK', 'ADMIN_MESSAGE', 'PROFILE_REVEAL', 'CHAT_REPLY', 'PAIR_CHAT'));
ALTER TABLE notification_history DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE match_results DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE final_selections DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE anonymous_questions DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE chat_message_reactions DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS reference_suppressed;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE chat_room_members DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE chat_rooms DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE video_reactions DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE video_upload_sessions DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE video_posts DROP COLUMN IF EXISTS member_removed_at;
ALTER TABLE room_members DROP COLUMN IF EXISTS member_removed_at;
DROP TABLE IF EXISTS room_member_removals;
DROP TABLE IF EXISTS mid_participation_invitations;
DROP TABLE IF EXISTS mid_participation_campaigns;
DROP TABLE IF EXISTS mid_participation_preferences;
COMMIT;

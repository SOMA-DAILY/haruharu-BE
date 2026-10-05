BEGIN;
-- 464 up 되돌리기: 두 CHECK 에 MATCH_CONSOLATION 을 다시 넣는다.
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS notification_history_type_check;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'VIDEO_REPLY', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'QUESTION_ANSWER_DEADLINE_SOON', 'QUESTION_SEND_DEADLINE_SOON', 'COIN_REFUNDED', 'MATCH_CONSOLATION', 'PAYMENT_COMPLETED', 'WINBACK', 'ADMIN_MESSAGE', 'PROFILE_REVEAL', 'CHAT_REPLY'));
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'coin_ledger'::regclass AND conname = 'coin_ledger_reason_check') THEN
    ALTER TABLE coin_ledger DROP CONSTRAINT coin_ledger_reason_check;
    ALTER TABLE coin_ledger ADD CONSTRAINT coin_ledger_reason_check CHECK (reason IN ('PURCHASE', 'MISSION_REWARD', 'MATCH_ENTER', 'MATCH_REFUND', 'CHAT_OPEN', 'REFUND_REVOKE', 'SIGNUP_BONUS', 'UPLOAD_COMPLETION_REWARD', 'MATCH_CONSOLATION'));
  END IF;
END $$;
-- uq_coin_ledger_match_consolation 은 원본 정의가 저장소에 없어 되살리지 않는다. 되돌린 코드도 원장 exists 검사로 1회 지급을 지킨다.
COMMIT;

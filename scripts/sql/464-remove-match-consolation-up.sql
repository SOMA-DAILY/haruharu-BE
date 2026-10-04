BEGIN;
-- 위로 구름(#143) 제거(#464). 코드에서 enum 값이 사라지므로 남은 행이 있으면 조회가 깨진다 — 있으면 중단한다.
DO $$ BEGIN IF EXISTS (SELECT 1 FROM notification_history WHERE type = 'MATCH_CONSOLATION') OR EXISTS (SELECT 1 FROM coin_ledger WHERE reason = 'MATCH_CONSOLATION') THEN RAISE EXCEPTION 'MATCH_CONSOLATION rows exist; delete them before removing the type'; END IF; END $$;
-- 알림 타입에서 MATCH_CONSOLATION 제외(455 판 목록 기준).
ALTER TABLE notification_history DROP CONSTRAINT IF EXISTS notification_history_type_check;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_type_check CHECK (type IN ('ROOM_MATCHED', 'ROOM_STARTED', 'SLOT_START', 'SLOT_DEADLINE_SOON', 'FINAL_SELECTION_OPEN', 'REACTION_RECEIVED', 'MUTUAL_MATCH', 'SELECTED_BY_SOMEONE', 'VIDEO_REPLY', 'CHAT', 'VIDEO_UPLOADED', 'QUESTION_AVAILABLE', 'QUESTION_RECEIVED', 'QUESTION_ANSWERED', 'QUESTION_ANSWER_DEADLINE_SOON', 'QUESTION_SEND_DEADLINE_SOON', 'COIN_REFUNDED', 'PAYMENT_COMPLETED', 'WINBACK', 'ADMIN_MESSAGE', 'PROFILE_REVEAL', 'CHAT_REPLY'));
-- coin_ledger.reason 의 CHECK 는 Hibernate 가 테이블 생성 시점에 만들었을 수 있다(환경마다 유무가 다름). 있을 때만 현재 enum 목록으로 다시 만든다.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'coin_ledger'::regclass AND conname = 'coin_ledger_reason_check') THEN
    ALTER TABLE coin_ledger DROP CONSTRAINT coin_ledger_reason_check;
    ALTER TABLE coin_ledger ADD CONSTRAINT coin_ledger_reason_check CHECK (reason IN ('PURCHASE', 'MISSION_REWARD', 'MATCH_ENTER', 'MATCH_REFUND', 'CHAT_OPEN', 'REFUND_REVOKE', 'SIGNUP_BONUS', 'UPLOAD_COMPLETION_REWARD'));
  END IF;
END $$;
-- 위로 구름 이중 지급 방지용 부분 유니크 인덱스(#143, 저장소 밖에서 수동 생성). 쓰는 곳이 없어졌다.
DROP INDEX IF EXISTS uq_coin_ledger_match_consolation;
COMMIT;

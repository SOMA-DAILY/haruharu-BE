-- #531 되돌리기. 서버만 옛 이미지로 되돌릴 때는 필요 없다(옛 서버는 새 칸이 있어도 동작한다).
-- 같은 상대에게 2일차·3일차 두 번 보낸 질문이 있으면 옛 유일 조건을 만들 수 없어 실패한다.
-- 그때는 질문을 지우지 말고 이 스크립트를 쓰지 않는다(옛 서버는 새 칸과 함께 그대로 동작한다).
BEGIN;
SET LOCAL lock_timeout = '5s';

ALTER TABLE anonymous_questions
    ADD CONSTRAINT uq_question_sender_target UNIQUE (room_id, sender_user_id, recipient_user_id);
ALTER TABLE anonymous_questions DROP CONSTRAINT IF EXISTS uq_question_sender_target_day;
ALTER TABLE anonymous_questions DROP CONSTRAINT IF EXISTS ck_question_day;
ALTER TABLE anonymous_questions DROP COLUMN IF EXISTS question_day;

-- 열림 기록은 알림·미확인 표시용 표식이라, 방·사람마다 가장 먼저 지급한 한 건만 남긴다.
DELETE FROM question_grants g
USING question_grants o
WHERE g.room_id = o.room_id AND g.user_id = o.user_id AND g.id > o.id;
ALTER TABLE question_grants ADD CONSTRAINT uq_question_grant UNIQUE (room_id, user_id);
ALTER TABLE question_grants DROP CONSTRAINT IF EXISTS uq_question_grant_day;
ALTER TABLE question_grants DROP CONSTRAINT IF EXISTS ck_question_grant_day;
ALTER TABLE question_grants DROP COLUMN IF EXISTS question_day;

COMMIT;

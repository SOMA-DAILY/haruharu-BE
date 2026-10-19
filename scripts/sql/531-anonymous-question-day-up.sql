-- #531 익명 질문 2일차·3일차 날짜별 1회.
-- 옛 서버(question_day 를 모름)와 함께 돌아도 안전하다: 옛 서버가 넣는 질문·열림 기록은 기본값 2(2일차)를 받는다.
-- 새 서버(ddl-auto=validate)를 띄우기 전에 적용한다. 여러 번 실행해도 같은 결과다.
BEGIN;
SET LOCAL lock_timeout = '5s';

ALTER TABLE anonymous_questions ADD COLUMN IF NOT EXISTS question_day INTEGER;
-- 기존 질문은 보낸 날짜로 채운다(방 시작 날짜 기준 달력 일차, 2~3으로 제한).
UPDATE anonymous_questions q
SET question_day = LEAST(3, GREATEST(2, (q.created_at::date - r.starts_at::date) + 1))
FROM rooms r
WHERE r.id = q.room_id AND q.question_day IS NULL AND r.starts_at IS NOT NULL;
UPDATE anonymous_questions SET question_day = 2 WHERE question_day IS NULL;
ALTER TABLE anonymous_questions ALTER COLUMN question_day SET DEFAULT 2;
ALTER TABLE anonymous_questions ALTER COLUMN question_day SET NOT NULL;
ALTER TABLE anonymous_questions DROP CONSTRAINT IF EXISTS ck_question_day;
ALTER TABLE anonymous_questions ADD CONSTRAINT ck_question_day CHECK (question_day IN (2, 3));
ALTER TABLE anonymous_questions DROP CONSTRAINT IF EXISTS uq_question_sender_target_day;
ALTER TABLE anonymous_questions
    ADD CONSTRAINT uq_question_sender_target_day
    UNIQUE (room_id, sender_user_id, recipient_user_id, question_day);
ALTER TABLE anonymous_questions DROP CONSTRAINT IF EXISTS uq_question_sender_target;
DROP INDEX IF EXISTS uq_question_sender_target;

ALTER TABLE question_grants ADD COLUMN IF NOT EXISTS question_day INTEGER;
UPDATE question_grants SET question_day = 2 WHERE question_day IS NULL;
ALTER TABLE question_grants ALTER COLUMN question_day SET DEFAULT 2;
ALTER TABLE question_grants ALTER COLUMN question_day SET NOT NULL;
ALTER TABLE question_grants DROP CONSTRAINT IF EXISTS ck_question_grant_day;
ALTER TABLE question_grants ADD CONSTRAINT ck_question_grant_day CHECK (question_day IN (2, 3));
ALTER TABLE question_grants DROP CONSTRAINT IF EXISTS uq_question_grant_day;
ALTER TABLE question_grants
    ADD CONSTRAINT uq_question_grant_day UNIQUE (room_id, user_id, question_day);
ALTER TABLE question_grants DROP CONSTRAINT IF EXISTS uq_question_grant;
DROP INDEX IF EXISTS uq_question_grant;

COMMIT;

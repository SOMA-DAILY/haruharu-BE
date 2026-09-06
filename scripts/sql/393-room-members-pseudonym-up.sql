BEGIN;

-- 방 세션 가명(#393). 입장 시 성별·순번으로 배정하며, 성별 미상이면 배정 못 해 null. 기존 멤버는 값이 없으므로 nullable.
-- 자유 문자열(풀 순환 시 숫자 접미사가 붙어 "민준2" 등)이라 값 도메인 CHECK 는 두지 않는다.
ALTER TABLE room_members ADD COLUMN IF NOT EXISTS pseudonym VARCHAR(30);

COMMIT;

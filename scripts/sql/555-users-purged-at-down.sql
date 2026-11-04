-- #555 롤백. 지우면 이미 파기한 회원을 다시 파기 대상으로 보지만 파기는 멱등이라 데이터 문제는 없다.
ALTER TABLE users DROP COLUMN IF EXISTS purged_at;

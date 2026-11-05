-- #555: 탈퇴 90일 파기 완료 시각. prod app 태스크는 ddl-auto=update 라 자동으로도 생기지만 명시적으로 둔다. 멱등.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE users ADD COLUMN IF NOT EXISTS purged_at TIMESTAMP(6);
COMMIT;

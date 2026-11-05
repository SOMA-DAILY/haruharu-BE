-- #554: 어드민 개인정보 접속기록(admin_access_logs)과 정기 점검 기록(admin_audit_reviews).
-- prod admin 태스크는 SPRING_JPA_HIBERNATE_DDL_AUTO=update 라 자동 생성되지만, 스키마 소유가 명시적이도록 같은 정의를 둔다. 멱등.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
CREATE TABLE IF NOT EXISTS admin_access_logs (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT,
    occurred_at TIMESTAMP(6) NOT NULL,
    ip          VARCHAR(45),
    method      VARCHAR(10) NOT NULL,
    path        VARCHAR(300) NOT NULL,
    action      VARCHAR(30) NOT NULL,
    target_type VARCHAR(30),
    target_id   BIGINT,
    status_code INTEGER NOT NULL,
    reason      VARCHAR(500),
    detail      VARCHAR(200),
    user_agent  VARCHAR(200)
);
CREATE INDEX IF NOT EXISTS idx_admin_access_logs_occurred ON admin_access_logs (occurred_at);
CREATE INDEX IF NOT EXISTS idx_admin_access_logs_admin ON admin_access_logs (admin_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_admin_access_logs_target ON admin_access_logs (target_type, target_id);
CREATE TABLE IF NOT EXISTS admin_audit_reviews (
    id          BIGSERIAL PRIMARY KEY,
    period      VARCHAR(7) NOT NULL,
    admin_id    BIGINT NOT NULL,
    note        VARCHAR(500),
    reviewed_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_reviews_period ON admin_audit_reviews (period);
COMMIT;

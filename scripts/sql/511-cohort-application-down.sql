-- #511 rollback: 신청 테이블·원장 USE_REFUND·프로필 번호를 걷어낸다. 이 기능을 쓰는 app/admin 을 먼저 이전 버전으로 되돌린 뒤 실행한다.
-- 신청·반환 원장·저장된 번호가 하나라도 있으면 멈춘다(운영 데이터 보호). 지워도 되는지 확인하고 직접 비운 뒤 다시 실행한다.
-- 다시 돌려도 안전하다. 테이블·칸이 없으면 행 검사를 건너뛴다.
BEGIN;
DO $$
BEGIN
    -- 존재 확인과 행 조회를 한 식에 두면 PL/pgSQL 이 식 전체를 먼저 해석해 없는 테이블에서 실패한다. 반드시 IF 를 나눈다.
    IF to_regclass('cohort_applications') IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM cohort_applications) THEN
            RAISE EXCEPTION 'cohort_applications has rows: back them up and clear them before rollback';
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM ticket_ledger WHERE reason = 'USE_REFUND' OR ref_type = 'COHORT_APPLICATION') THEN
        RAISE EXCEPTION 'ticket_ledger has cohort application rows: the old version cannot read USE_REFUND';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'user_profiles' AND column_name = 'phone') THEN
        IF EXISTS (SELECT 1 FROM user_profiles WHERE phone IS NOT NULL) THEN
            RAISE EXCEPTION 'user_profiles.phone has values: back them up and clear them before rollback';
        END IF;
    END IF;
END $$;

DROP TABLE IF EXISTS cohort_applications;
DROP INDEX IF EXISTS ux_ticket_ledger_cohort_application_ref;

DO $$
DECLARE r record;
BEGIN
    -- 정의문 글자가 아니라 reason 칸에 걸린 CHECK 만 고른다(다른 칸의 CHECK 를 잘못 지우지 않게).
    FOR r IN SELECT DISTINCT con.conname FROM pg_constraint con
             JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
             WHERE con.conrelid = 'ticket_ledger'::regclass AND con.contype = 'c' AND att.attname = 'reason' LOOP
        EXECUTE format('ALTER TABLE ticket_ledger DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;
ALTER TABLE ticket_ledger ADD CONSTRAINT ticket_ledger_reason_check
    CHECK (reason IN ('PURCHASE', 'USE', 'REFUND_REVOKE', 'ADMIN_GRANT'));

ALTER TABLE user_profiles DROP COLUMN IF EXISTS phone;
COMMIT;

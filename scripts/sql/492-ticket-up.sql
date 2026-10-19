-- #492. 코인 → 티켓 전환. app/admin 쓰기를 멈춘 상태에서 새 버전을 띄우기 전에 1회 실행한다. 재실행은 의도적으로 실패한다.
-- 새 버전을 먼저 띄우면 ddl-auto=update 가 빈 ticket_* 테이블을 새로 만들고 기존 지갑이 옮겨지지 않는다.
-- 코인 잔액·원장·상품·구매는 티켓으로 환산할 근거가 없어 옮기지 않는다. 남아 있으면 멈추므로 보관 후 비우고 다시 실행한다.
BEGIN;
LOCK TABLE coin_wallet, coin_ledger, iap_product, iap_purchase IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF to_regclass('ticket_wallet') IS NOT NULL OR to_regclass('ticket_ledger') IS NOT NULL THEN
        RAISE EXCEPTION 'ticket_* tables already exist: drop the empty ones created by the new version first';
    END IF;
    IF EXISTS (SELECT 1 FROM coin_wallet WHERE paid_balance <> 0 OR reward_balance <> 0) THEN
        RAISE EXCEPTION 'coin_wallet has non-zero balances: coin balances cannot be converted to tickets';
    END IF;
    IF EXISTS (SELECT 1 FROM coin_ledger) THEN
        RAISE EXCEPTION 'coin_ledger has rows: archive and clear them before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM iap_product) OR EXISTS (SELECT 1 FROM iap_purchase) THEN
        RAISE EXCEPTION 'iap_product/iap_purchase have rows: coin amounts cannot be read as ticket amounts';
    END IF;
END $$;

ALTER TABLE coin_wallet RENAME TO ticket_wallet;
ALTER TABLE ticket_wallet RENAME COLUMN paid_balance TO balance;
ALTER TABLE ticket_wallet DROP COLUMN reward_balance;

ALTER TABLE coin_ledger RENAME TO ticket_ledger;
ALTER TABLE ticket_ledger DROP COLUMN bucket;
ALTER TABLE ticket_ledger ADD COLUMN memo varchar(200);
-- 엔티티는 varchar(40)이다. ddl-auto=update 는 기존 컬럼 길이를 늘리지 않으므로 여기서 맞춘다.
ALTER TABLE ticket_ledger ALTER COLUMN reason TYPE varchar(40);

-- 제약·인덱스·시퀀스 이름도 옮긴다. 환경마다 Hibernate 가 만든 이름이 달라 있는 것만 바꾼다.
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT conrelid::regclass AS tbl, conname FROM pg_constraint
             WHERE conrelid IN ('ticket_wallet'::regclass, 'ticket_ledger'::regclass) AND conname LIKE 'coin\_%' LOOP
        EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I', r.tbl, r.conname, regexp_replace(r.conname, '^coin_', 'ticket_'));
    END LOOP;
    FOR r IN SELECT relname, relkind FROM pg_class
             WHERE relkind IN ('i', 'S') AND relnamespace = current_schema()::regnamespace
               AND (relname LIKE 'coin\_wallet%' OR relname LIKE 'coin\_ledger%' OR relname LIKE 'idx\_coin\_ledger%') LOOP
        EXECUTE format('ALTER %s %I RENAME TO %I', CASE r.relkind WHEN 'S' THEN 'SEQUENCE' ELSE 'INDEX' END,
                       r.relname, replace(r.relname, 'coin_', 'ticket_'));
    END LOOP;
    -- reason CHECK 는 Hibernate 가 테이블 생성 시점에 만들었을 수 있다(환경마다 유무가 다름). 있으면 걷어내고 새 목록으로 만든다.
    FOR r IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'ticket_ledger'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%reason%' LOOP
        EXECUTE format('ALTER TABLE ticket_ledger DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;
ALTER TABLE ticket_ledger ADD CONSTRAINT ticket_ledger_reason_check
    CHECK (reason IN ('PURCHASE', 'USE', 'REFUND_REVOKE', 'ADMIN_GRANT'));

ALTER TABLE iap_product RENAME COLUMN coin_amount TO ticket_amount;
ALTER TABLE iap_product DROP COLUMN bonus_amount;

ALTER TABLE iap_purchase RENAME COLUMN coin_amount TO ticket_amount;
ALTER TABLE iap_purchase ADD COLUMN unrevoked_ticket_amount bigint NOT NULL DEFAULT 0;
COMMIT;

-- #492 되돌리기. 쓰기를 멈춘 상태에서 구버전을 띄우기 전에 실행한다.
-- 새 버전이 티켓 원장·상품·구매를 한 건이라도 썼으면 코인 모델로 무손실 복귀할 수 없어 멈춘다. 그 경우 새 버전에서 고쳐 나간다.
BEGIN;
LOCK TABLE ticket_wallet, ticket_ledger, iap_product, iap_purchase IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM ticket_wallet WHERE balance <> 0) OR EXISTS (SELECT 1 FROM ticket_ledger) THEN
        RAISE EXCEPTION 'Ticket balances or ledger rows exist: keep the new schema and roll forward';
    END IF;
    IF EXISTS (SELECT 1 FROM iap_product) OR EXISTS (SELECT 1 FROM iap_purchase) THEN
        RAISE EXCEPTION 'Ticket products or purchases exist: keep the new schema and roll forward';
    END IF;
END $$;

ALTER TABLE iap_purchase DROP COLUMN unrevoked_ticket_amount;
ALTER TABLE iap_purchase RENAME COLUMN ticket_amount TO coin_amount;

ALTER TABLE iap_product ADD COLUMN bonus_amount bigint NOT NULL DEFAULT 0;
ALTER TABLE iap_product RENAME COLUMN ticket_amount TO coin_amount;

ALTER TABLE ticket_ledger DROP CONSTRAINT ticket_ledger_reason_check;
ALTER TABLE ticket_ledger DROP COLUMN memo;
ALTER TABLE ticket_ledger ADD COLUMN bucket varchar(10) NOT NULL DEFAULT 'PAID';
ALTER TABLE ticket_ledger ALTER COLUMN bucket DROP DEFAULT;
ALTER TABLE ticket_ledger RENAME TO coin_ledger;

ALTER TABLE ticket_wallet ADD COLUMN reward_balance bigint NOT NULL DEFAULT 0;
ALTER TABLE ticket_wallet ALTER COLUMN reward_balance DROP DEFAULT;
ALTER TABLE ticket_wallet RENAME COLUMN balance TO paid_balance;
ALTER TABLE ticket_wallet RENAME TO coin_wallet;

DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT conrelid::regclass AS tbl, conname FROM pg_constraint
             WHERE conrelid IN ('coin_wallet'::regclass, 'coin_ledger'::regclass) AND conname LIKE 'ticket\_%' LOOP
        EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I', r.tbl, r.conname, regexp_replace(r.conname, '^ticket_', 'coin_'));
    END LOOP;
    FOR r IN SELECT relname, relkind FROM pg_class
             WHERE relkind IN ('i', 'S') AND relnamespace = current_schema()::regnamespace
               AND (relname LIKE 'ticket\_wallet%' OR relname LIKE 'ticket\_ledger%' OR relname LIKE 'idx\_ticket\_ledger%') LOOP
        EXECUTE format('ALTER %s %I RENAME TO %I', CASE r.relkind WHEN 'S' THEN 'SEQUENCE' ELSE 'INDEX' END,
                       r.relname, replace(r.relname, 'ticket_', 'coin_'));
    END LOOP;
END $$;
-- 구버전의 reason CHECK 는 환경마다 유무가 달랐으므로 되살리지 않는다. 구버전 코드는 CHECK 없이도 동작한다.
COMMIT;

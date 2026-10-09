-- #492 읽기 전용. 대상 환경에서 up 전에 실행해 옮길 데이터가 남아 있는지 확인한다. 유저 식별자는 출력하지 않는다.
SELECT to_regclass('coin_wallet') AS coin_wallet, to_regclass('coin_ledger') AS coin_ledger,
       to_regclass('ticket_wallet') AS ticket_wallet, to_regclass('ticket_ledger') AS ticket_ledger;

-- 아래 네 값이 모두 0이어야 up이 통과한다.
SELECT count(*) AS wallets_with_balance FROM coin_wallet WHERE paid_balance <> 0 OR reward_balance <> 0;
SELECT count(*) AS coin_ledger_rows FROM coin_ledger;
SELECT count(*) AS iap_product_rows FROM iap_product;
SELECT count(*) AS iap_purchase_rows FROM iap_purchase;

-- 이름을 함께 옮길 제약·인덱스·시퀀스.
SELECT conrelid::regclass AS table_name, conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint WHERE conrelid IN ('coin_wallet'::regclass, 'coin_ledger'::regclass) ORDER BY 1, 2;
SELECT relname, relkind FROM pg_class
WHERE relkind IN ('i', 'S') AND (relname LIKE 'coin\_wallet%' OR relname LIKE 'coin\_ledger%' OR relname LIKE 'idx\_coin\_ledger%')
ORDER BY 1;

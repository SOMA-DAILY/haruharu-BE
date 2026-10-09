-- #492 up 직후, 쓰기를 멈춘 상태에서 실행한다. 어긋나면 예외로 멈춘다.
DO $$
BEGIN
    IF to_regclass('coin_wallet') IS NOT NULL OR to_regclass('coin_ledger') IS NOT NULL THEN
        RAISE EXCEPTION 'coin_* tables still exist';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema()
                 AND ((table_name = 'ticket_wallet' AND column_name IN ('paid_balance', 'reward_balance'))
                   OR (table_name = 'ticket_ledger' AND column_name = 'bucket')
                   OR (table_name IN ('iap_product', 'iap_purchase') AND column_name IN ('coin_amount', 'bonus_amount')))) THEN
        RAISE EXCEPTION 'Legacy coin columns remain';
    END IF;
    IF (SELECT count(*) FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND ((table_name = 'ticket_wallet' AND column_name = 'balance')
            OR (table_name = 'ticket_ledger' AND column_name = 'memo')
            OR (table_name = 'iap_product' AND column_name = 'ticket_amount')
            OR (table_name = 'iap_purchase' AND column_name IN ('ticket_amount', 'unrevoked_ticket_amount')))) <> 5 THEN
        RAISE EXCEPTION 'Ticket columns are missing';
    END IF;
    IF EXISTS (SELECT 1 FROM ticket_wallet WHERE balance <> 0) THEN
        RAISE EXCEPTION 'Wallets must start with zero tickets';
    END IF;
    -- 유저마다 지갑이 하나씩 그대로 남아 있어야 한다(가입 시점 생성, #298).
    IF EXISTS (SELECT 1 FROM ticket_wallet GROUP BY user_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate wallets per user';
    END IF;
END $$;

SELECT count(*) AS wallets FROM ticket_wallet;
SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid)
FROM pg_constraint WHERE conrelid IN ('ticket_wallet'::regclass, 'ticket_ledger'::regclass) ORDER BY 1, 2;
SELECT relname, relkind FROM pg_class
WHERE relkind IN ('i', 'S') AND (relname LIKE '%coin%') ORDER BY 1;

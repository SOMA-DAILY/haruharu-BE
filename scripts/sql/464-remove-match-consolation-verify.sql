SELECT count(*) AS notification_rows FROM notification_history WHERE type = 'MATCH_CONSOLATION';
SELECT count(*) AS coin_ledger_rows FROM coin_ledger WHERE reason = 'MATCH_CONSOLATION';
SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid IN ('notification_history'::regclass, 'coin_ledger'::regclass) AND contype = 'c' ORDER BY 1, 2;
SELECT indexname FROM pg_indexes WHERE tablename = 'coin_ledger' AND indexname = 'uq_coin_ledger_match_consolation';

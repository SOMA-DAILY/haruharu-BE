-- #505 up 직후 실행한다. 엔티티와 어긋나면 예외로 멈춘다(ddl-auto=validate 와 같은 기준).
DO $$
BEGIN
    IF to_regclass('push_bundles') IS NULL THEN
        RAISE EXCEPTION 'push_bundles does not exist';
    END IF;
    IF (SELECT count(*) FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'push_bundles'
          AND column_name IN ('id', 'user_id', 'bundle_key', 'last_alerted_at', 'last_alerted_ref',
                              'names', 'created_at', 'updated_at')) <> 8 THEN
        RAISE EXCEPTION 'push_bundles columns are missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'push_bundles'::regclass AND conname = 'ux_push_bundles_user_key') THEN
        RAISE EXCEPTION 'ux_push_bundles_user_key is missing';
    END IF;
END $$;

SELECT column_name, data_type, is_nullable, character_maximum_length
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'push_bundles' ORDER BY ordinal_position;

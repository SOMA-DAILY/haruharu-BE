-- 읽기 전용 전환 게이트. 실패하면 새 코드 기동/쓰기 재개 전에 원인을 해결한다.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'chat_rooms' AND column_name = 'close_at'
          AND data_type = 'timestamp without time zone' AND is_nullable = 'YES' AND column_default IS NULL
    ) THEN
        RAISE EXCEPTION 'close_at must be nullable timestamp without default';
    END IF;
    IF EXISTS (
        SELECT 1 FROM chat_rooms c LEFT JOIN rooms r ON r.id = c.room_id
        WHERE c.type = 'GROUP' AND c.deleted_at IS NULL AND c.close_at IS NULL
          AND (r.id IS NULL OR r.deleted_at IS NOT NULL OR r.status IN ('ENDED', 'CANCELLED'))
    ) THEN
        RAISE EXCEPTION 'Unclosed terminal/orphan GROUP remains: repeat backfill';
    END IF;
    IF EXISTS (
        SELECT 1 FROM chat_rooms c JOIN rooms r ON r.id = c.room_id
        WHERE c.type = 'GROUP' AND c.deleted_at IS NULL AND c.close_at IS NULL
          AND r.deleted_at IS NULL AND r.status NOT IN ('ACTIVE', 'SELECTING', 'ENDED', 'CANCELLED')
    ) THEN
        RAISE EXCEPTION 'Open GROUP has unexpected source status: inspect before cutover';
    END IF;
END $$;

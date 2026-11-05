-- 읽기 전용: status CHECK 가 SANCTIONED / REJECTED 를 받는지와 처리자 칸 2개를 확인한다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'reports'::regclass AND conname = 'reports_status_check'
                   AND pg_get_constraintdef(oid) LIKE '%SANCTIONED%REJECTED%') THEN
        RAISE EXCEPTION 'reports_status_check rejects SANCTIONED/REJECTED; run 553-report-resolution-up.sql';
    END IF;
END $$;
SELECT column_name FROM information_schema.columns
WHERE table_name = 'reports' AND column_name IN ('resolved_by_admin_id', 'resolved_at') ORDER BY 1;

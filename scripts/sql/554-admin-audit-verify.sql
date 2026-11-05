SELECT to_regclass('admin_access_logs') AS access_logs, to_regclass('admin_audit_reviews') AS reviews;
SELECT indexname FROM pg_indexes WHERE tablename IN ('admin_access_logs', 'admin_audit_reviews') ORDER BY indexname;

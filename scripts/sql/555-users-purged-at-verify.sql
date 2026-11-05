SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'purged_at';
SELECT count(*) AS deleted_not_purged FROM users WHERE status = 'DELETED' AND purged_at IS NULL;

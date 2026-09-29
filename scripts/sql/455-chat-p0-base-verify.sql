SELECT column_name, data_type, character_maximum_length, is_nullable FROM information_schema.columns WHERE table_name='user_profiles' AND column_name IN ('birth_year','department','into_now') ORDER BY column_name;
SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='notification_history'::regclass AND conname='notification_history_type_check';
-- 시스템 유저 id → env CHAT_SYSTEM_USER_ID
SELECT u.id AS chat_system_user_id, u.status, p.nickname FROM users u JOIN user_profiles p ON p.user_id = u.id WHERE p.nickname = '하루' AND p.deleted_at IS NULL AND NOT EXISTS (SELECT 1 FROM user_credentials c WHERE c.user_id = u.id);

-- 컬럼·제약 확인
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name = 'user_profiles' AND column_name = 'gender';

SELECT conname, pg_get_constraintdef(oid) AS def
FROM pg_constraint
WHERE conrelid = 'user_profiles'::regclass AND conname = 'ck_user_profiles_gender';

-- 값 도메인 위반(0 이어야 함)
SELECT count(*) AS invalid_gender
FROM user_profiles
WHERE gender IS NOT NULL AND gender NOT IN ('MALE', 'FEMALE');

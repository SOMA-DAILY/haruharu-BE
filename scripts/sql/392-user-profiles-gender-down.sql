BEGIN;

ALTER TABLE user_profiles DROP CONSTRAINT IF EXISTS ck_user_profiles_gender;
ALTER TABLE user_profiles DROP COLUMN IF EXISTS gender;

COMMIT;

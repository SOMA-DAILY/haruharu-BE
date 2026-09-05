BEGIN;

-- 온보딩 자기신고 성별(#392). 기존 프로필은 값이 없으므로 nullable 로 추가한다.
-- 필수는 온보딩 완료 게이트(ProfileService.hasCompletedProfile)가 강제한다 — 프로필 PUT 은 편집 호환을 위해 gender 미전송을 허용한다.
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS gender VARCHAR(10);

-- 값 도메인 가드(enum 정합 — #381 방침). null 은 허용.
ALTER TABLE user_profiles DROP CONSTRAINT IF EXISTS ck_user_profiles_gender;
ALTER TABLE user_profiles ADD CONSTRAINT ck_user_profiles_gender
    CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE'));

COMMIT;

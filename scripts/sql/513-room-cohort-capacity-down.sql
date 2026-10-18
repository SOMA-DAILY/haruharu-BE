-- #513 rollback: 방 기수·이름·성별 정원과 멤버 입장 경로·배정 신청 칸을 걷어낸다.
-- 이 기능을 쓰는 app/admin 을 먼저 이전 버전으로 되돌린 뒤 실행한다.
-- 앱 신청으로 들어온 멤버나 기수·이름·정원이 적힌 방이 하나라도 있으면 멈춘다(운영 데이터 보호).
-- 지워도 되는지 확인하고 직접 비운 뒤 다시 실행한다. 다시 돌려도 안전하다(칸이 없으면 행 검사를 건너뛴다).
BEGIN;
DO $$
BEGIN
    -- 존재 확인과 행 조회를 한 식에 두면 PL/pgSQL 이 식 전체를 먼저 해석해 없는 칸에서 실패한다. 반드시 IF 를 나눈다.
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'room_members' AND column_name = 'entry_path') THEN
        IF EXISTS (SELECT 1 FROM room_members WHERE entry_path <> 'INVITE' OR application_id IS NOT NULL) THEN
            RAISE EXCEPTION 'room_members has APPLICATION members: the old version counts them toward the invite capacity';
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'rooms' AND column_name = 'cohort_id') THEN
        IF EXISTS (SELECT 1 FROM rooms WHERE cohort_id IS NOT NULL OR name IS NOT NULL
                   OR male_capacity IS NOT NULL OR female_capacity IS NOT NULL) THEN
            RAISE EXCEPTION 'rooms has cohort/name/capacity values: back them up and clear them before rollback';
        END IF;
    END IF;
END $$;

DROP INDEX IF EXISTS ux_room_members_application;
DROP INDEX IF EXISTS idx_rooms_cohort;
-- 칸을 지우면 그 칸의 FK 도 함께 지워진다.
ALTER TABLE room_members DROP COLUMN IF EXISTS application_id;
ALTER TABLE room_members DROP COLUMN IF EXISTS entry_path;
ALTER TABLE rooms DROP COLUMN IF EXISTS cohort_id;
ALTER TABLE rooms DROP COLUMN IF EXISTS name;
ALTER TABLE rooms DROP COLUMN IF EXISTS male_capacity;
ALTER TABLE rooms DROP COLUMN IF EXISTS female_capacity;
COMMIT;

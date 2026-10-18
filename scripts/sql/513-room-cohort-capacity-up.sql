-- #513: 방 기수·이름·성별 정원(rooms) + 멤버 입장 경로·배정 신청(room_members).
-- 510-recruit-cohort-up.sql·511-cohort-application-up.sql 다음, app/admin 배포 전에 적용한다.
-- 재실행해도 안전하다. 새 버전을 먼저 띄워 ddl-auto 가 칸을 만들었어도, 다시 돌리면 빠진 기본값·제약·인덱스만 채운다.
-- 기존 멤버는 모두 초대코드로 들어왔으므로 entry_path 는 INVITE 로 채운다(상수 DEFAULT 라 행을 다시 쓰지 않는다).
-- CHECK 는 걸지 않는다. 값 범위·짝 규칙(정원 ≥ 1, APPLICATION ⇔ 신청 id)은 도메인(RoomComposition·RoomMember)이 한 곳에서 지킨다.
-- DB 에는 참조와 중복만 둔다. JPA 로 표현할 수 없어 이 스크립트에만 있다.
--   fk_rooms_cohort / fk_room_members_application   기수·신청 참조
--   ux_room_members_application                     한 신청은 살아 있는 멤버 하나로만 들어간다(동시 배정의 최종 방어)
-- FK 를 거는 동안 rooms·room_members 에 짧은 잠금이 걸린다(행이 적어 짧다).
BEGIN;
DO $$
BEGIN
    IF to_regclass('rooms') IS NULL OR to_regclass('room_members') IS NULL THEN
        RAISE EXCEPTION 'rooms/room_members do not exist';
    END IF;
    IF to_regclass('cohorts') IS NULL OR to_regclass('cohort_applications') IS NULL THEN
        RAISE EXCEPTION 'cohorts/cohort_applications do not exist: apply 510-recruit-cohort-up.sql and 511-cohort-application-up.sql first';
    END IF;
END $$;

ALTER TABLE rooms ADD COLUMN IF NOT EXISTS cohort_id BIGINT;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS name VARCHAR(50);
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS male_capacity INTEGER;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS female_capacity INTEGER;

ALTER TABLE room_members ADD COLUMN IF NOT EXISTS entry_path VARCHAR(20) NOT NULL DEFAULT 'INVITE';
ALTER TABLE room_members ADD COLUMN IF NOT EXISTS application_id BIGINT;
-- ddl-auto 가 먼저 칸을 만들었어도 기본값·NOT NULL 을 같게 맞춘다.
UPDATE room_members SET entry_path = 'INVITE' WHERE entry_path IS NULL;
ALTER TABLE room_members ALTER COLUMN entry_path SET DEFAULT 'INVITE';
ALTER TABLE room_members ALTER COLUMN entry_path SET NOT NULL;

-- ddl-auto 가 먼저 칸을 만들었으면 Hibernate 가 enum 칸에 이름 없는 CHECK 를 붙인다(room_members_entry_path_check).
-- 배포 순서(SQL 먼저 / 앱 먼저)와 관계없이 같은 모양이 되게 이번에 더한 칸의 CHECK 는 모두 걷어낸다.
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT DISTINCT con.conrelid::regclass::text AS tbl, con.conname FROM pg_constraint con
             JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
             WHERE con.contype = 'c'
               AND ((con.conrelid = 'room_members'::regclass AND att.attname IN ('entry_path', 'application_id'))
                 OR (con.conrelid = 'rooms'::regclass
                     AND att.attname IN ('cohort_id', 'name', 'male_capacity', 'female_capacity'))) LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', r.tbl, r.conname);
    END LOOP;
END $$;

-- FK 는 이름으로 있는지 보고 없을 때만 건다(ddl-auto 가 먼저 만든 테이블에도 같은 이름으로 맞춘다).
DO $$
DECLARE c record;
BEGIN
    FOR c IN SELECT * FROM (VALUES
        ('rooms', 'fk_rooms_cohort', 'FOREIGN KEY (cohort_id) REFERENCES cohorts (id)'),
        ('room_members', 'fk_room_members_application', 'FOREIGN KEY (application_id) REFERENCES cohort_applications (id)')
    ) AS t(tbl, name, def) LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = c.tbl::regclass AND conname = c.name) THEN
            EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I %s', c.tbl, c.name, c.def);
        END IF;
    END LOOP;
END $$;

-- 기수별 방 조회(#515·#516). 이름은 RoomEntity @Index 와 같다.
CREATE INDEX IF NOT EXISTS idx_rooms_cohort ON rooms (cohort_id);
-- 한 신청은 살아 있는 멤버 하나로만 들어간다. 배정 경로(#516)가 먼저 막고, 이 인덱스가 최종으로 막는다.
CREATE UNIQUE INDEX IF NOT EXISTS ux_room_members_application ON room_members (application_id)
    WHERE application_id IS NOT NULL AND deleted_at IS NULL;
COMMIT;

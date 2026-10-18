-- #510 rollback: 트랙·기수 테이블을 지운다. 이 테이블을 쓰는 app/admin 을 먼저 이전 버전으로 되돌린 뒤 실행한다.
-- 트랙·기수가 하나라도 있으면 멈춘다(운영 데이터 보호). 지워도 되는지 확인하고 직접 비운 뒤 다시 실행한다.
-- 다시 돌려도 안전하다. 테이블이 없으면 행 검사를 건너뛴다.
BEGIN;
DO $$
BEGIN
    -- 존재 확인과 행 조회를 한 식에 두면 PL/pgSQL 이 식 전체를 먼저 해석해 없는 테이블에서 실패한다. 반드시 IF 를 나눈다.
    IF to_regclass('cohorts') IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM cohorts) THEN
            RAISE EXCEPTION 'cohorts has rows: back them up and clear them before rollback';
        END IF;
    END IF;
    IF to_regclass('recruit_tracks') IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM recruit_tracks) THEN
            RAISE EXCEPTION 'recruit_tracks has rows: back them up and clear them before rollback';
        END IF;
    END IF;
END $$;
DROP TABLE IF EXISTS cohorts;
DROP TABLE IF EXISTS recruit_tracks;
COMMIT;

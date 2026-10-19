-- #505 rollback: 묶음 상태만 지운다. 이 테이블을 쓰는 app 을 먼저 이전 버전으로 되돌린 뒤 실행한다.
-- 다시 올리면 모든 수신자의 다음 알림이 한 번씩 울리는 것 외에 잃는 데이터는 없다.
BEGIN;
DROP TABLE IF EXISTS push_bundles;
COMMIT;

# 관리자 방 기수 묶음

모집 트랙/신청 기수와 독립된 관리자 전용 방 묶음이다. 일반 앱의 방 조회·입장·일정·알림에는 관여하지 않는다.

- `GET /api/v1/admin/room-groups?page=0&size=20`: 최신 생성 순 기수. 최대 100개 페이지.
- `POST /api/v1/admin/room-groups`: `{name}` (공백 제거 후 1~50자, 중복 409).
- `GET /api/v1/admin/rooms`: 기존 필터에 `groupId`, `ungrouped`, `isTest` 추가. 목록·개수 모두 같은 서버 조건 적용.
- `PATCH /api/v1/admin/rooms/organization`: `{room_ids, group_id?, clear_group?, is_test?}`. 1~100개, 기수/구분 생략 시 유지. `clear_group=true`로 기수만 해제. group_id와 clear_group 동시 지정 또는 변경할 필드 없음은 400. 존재하지 않는 방/기수는 404이며 전체 원자적으로 실패한다.
- 기존 `/rooms/{id}/type`도 같은 원자적 지정 경로를 사용한다.
- 방 목록/상세에 `group_id`, `is_test` 추가. 상세에는 `group_name`도 포함. 기존 방은 메타데이터가 없어 두 값 모두 null이다. 운영/테스트로 임의 추정하지 않는다.
- 방 생성 요청의 `group_id`는 미지정 시 최신 기수, `is_test` 미지정 시 운영방(false)으로 저장한다. 방·코드·미션·분류를 한 트랜잭션에 저장한다.
- 조회는 로그인한 관리자 모두, 쓰기는 SUPER/OPERATOR만 허용한다.

## 배포

관리자 서버의 ddl-auto는 none이다. 먼저 `scripts/sql/536-admin-room-groups-up.sql`을 트랜잭션 안에서 적용하고 `536-admin-room-groups-verify.sql`을 확인한다. 관리자 전용 테이블/인덱스만 추가하며 기존 방 행은 갱신하지 않는다. 롤백 시 이전 관리자 서버·웹 버전으로 되돌리고 추가 테이블은 보존한다. 일반 앱 서버 재배포는 필요 없다.

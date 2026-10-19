# #482 자동 미션 폐기와 데이터 호환

## 적용 범위

롤백 커밋 `d4ca12b`는 #478·#477을 되돌린다. 기준은 #476 머지
`2ab8179b2b3f2df7b4c7ccb144127f6f01a6f4ac`이며, #477의 STOMP 위조 방지 검사와
회귀 테스트는 사용자 요청으로 보존했다. 롤백만 한 중간 커밋은 자동 미션을 다시 활성화하므로
배포하지 않는다. 뒤이은 폐기 변경과 함께 적용한다.

자동 미션의 방 설정·슬롯 생성, 후보 카탈로그·revision, 배정 정책·incident, 카드·노출 기록,
영상·채팅 활동 수집, 결과 집계, 두 Spring Batch Job과 런처를 제거했다.
일반 영상 촬영 슬롯(`SlotClock`), today/feed, 업로드 세션, 일반 채팅·답장·반응,
#457 운영 글 편성·프로필 공개, 최종 선택과 #476 질문 제한은 유지한다.
사용자 Role, 발신자 모델, Redis 발송 구조, 새 미션은 후속 작업이다.

## 클라이언트와 운영 설정 변경

| 계약 | 변경 후 동작 |
| --- | --- |
| 방 생성의 `mission_enabled`, `mission_slots` | 필수 입력 및 Swagger 필드에서 제거. 구형 JSON 입력은 무시되며 미션이 생성되지 않음 |
| `GET /api/v1/rooms/{roomId}/missions` | 제거, 인증된 요청 404 |
| `POST /api/v1/rooms/{roomId}/missions/{roomMissionId}/impression` | 제거, 인증된 요청 404 |
| `/api/v1/admin/missions`와 revision·상태 하위 경로 | 제거, 인증된 요청 404 |
| #477의 operator-missions·mission-image·mission-recording | 롤백으로 제거 |
| `PATCH /api/v1/video-posts/{videoPostId}/caption` | 롤백으로 제거했다가 #498에서 복구 |
| 영상·업로드 세션 캡션 | 롤백으로 20자가 됐다가 #498에서 최대 100자로 복구 |
| `missionAssignmentJob`, `missionOutcomeJob` 내부 실행 | Job 등록 제거, 인증된 내부 요청 404, 실행 기록 추가 없음 |
| `MISSION_ASSIGNMENT_*`, `MISSION_OUTCOME_*` | 소비하는 코드 제거. 예전 환경변수가 남아 있어도 재활성화되지 않음 |

보호 경로의 미인증 요청은 기존대로 401이다. 방 생성·초대코드·운영 글 쓰기 권한은
SUPER/OPERATOR로 유지한다. FE/admin 저장소는 이번 작업에서 수정하지 않았다.
배포 전 구형 API 사용 여부를 해당 클라이언트에서 확인해야 한다.

## 과거 데이터 보존

다음 테이블의 JPA 매핑과 실행 코드는 제거하지만 실제 테이블·행은 삭제하지 않는다.

- `missions`, `mission_revisions`, `mission_revision_eligibilities`
- `room_mission_settings`, `room_mission_slots`, `room_missions`
- `mission_assignment_incidents`, `mission_member_states`, `mission_outcomes`
- #477의 `operator_missions`, `operator_mission_images`

`video_posts.mission_id`와 응답의 `mission_id`는 과거 참조값 조회 호환용으로 남긴다.
신규 영상은 null을 기록하며, 미션 테이블을 조회하거나 미션을 실행하지 않는다.
채팅의 과거 미션 메타데이터 컬럼은 DB에 남아도 무시된다. 기본 ADMIN 메시지 내용과
기존 삭제 표시를 사용하며, 새 미션 카드 메타데이터는 응답하지 않는다.

캡션의 저장 매핑은 `VARCHAR(100)`으로 유지한다. `VARCHAR(20)`으로 축소하거나 긴 캡션을
잘라내지 않는다. app prod는 `ddl-auto=validate`, admin 기본은 `none`, 두 dev 프로필은
`update`이므로 특히 dev도 축소 매핑을 사용하지 않는다. #498에서 입력 제한을 100자로
복구했으므로, 확장 SQL이 미적용된 20자 DB에는 배포 전에 `scripts/sql/475-video-caption-up.sql`을
적용하고 `475-video-caption-verify.sql`로 확인한다. 이번 작업에서 실제 환경 DB나 S3는 변경하지 않았다.

361·363·365·367·371·379·402·449 SQL은 적용 이력과 검증을 위해 원래 위치에 보존한다.
신규 설치에 미션 테이블은 필요 없다. 해당 up/down SQL을 폐기 절차로 실행하지 않는다.
#474 SQL은 롤백으로 현재 트리에서 빠졌지만 다음 이력에서 원문을 확인할 수 있다. #475 SQL은 #498에서 복원했다.

```sh
git show 37fdb27ca5e5ffe87d180fdf187b5d7209e3b2e1:scripts/sql/474-operator-missions-up.sql
```

## 배포 전 확인

실제 dev/prod의 SQL 적용 여부·잔존 예약·긴 캡션 건수는 아직 확인하지 않았다.
이 문서와 로컬 테스트 통과만으로 실제 환경 배포 검증이 완료된 것은 아니다.

1. `scripts/sql/482-mission-retirement-verify.sql`을 읽기 전용으로 실행해 테이블/컬럼 존재,
   잔존 상태·예약·긴 캡션의 건수를 기록한다. 콘텐츠·사용자 식별자는 출력하지 않는다.
2. FE/admin에서 폐기 경로 호출을 제거한다. 캡션 제한은 #498 기준 100자다.
3. 구버전 app/admin 인스턴스와 외부 오케스트레이터의 미션 Job 호출이 남지 않게 교체한다.
   저장소의 배포 워크플로에는 두 Job의 외부 호출이 발견되지 않았다. 외부 운영 설정은 별도 확인한다.
4. 교체 후 읽기 전용 집계를 재실행해 미션 데이터가 추가 생성·갱신되지 않는지 확인한다.
   기존 예약을 SENT로 바꾸거나 결과를 임의 생성하지 않는다.
5. 과거 미션 테이블·메시지·사진 삭제는 별도 보존·복구 계획이 있는 후속 작업에서 다룬다.

## 검증 범위

방 생성·초대코드·편성 원자성, 구형 입력 무시, 폐기 API/Job, 일반 슬롯·영상·채팅·질문 및
운영 글 회귀 테스트를 실행한다. PostgreSQL에서는 기존 설정·미래 슬롯 보존과 긴 캡션 세션
완료·재시도를 검증한다. SQL 이력 테스트는 유지한다. 실제 환경의 데이터 분포와 클라이언트
호환 상태는 로컬 합성 데이터 테스트로 대신 확인할 수 없다.

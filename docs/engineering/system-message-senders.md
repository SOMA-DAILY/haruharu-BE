# 시스템 메시지 발신자와 편성 실행 (#484)

구현 기준은 `refactor/482`의 `719d4b6`이다. 자동 미션 엔진을 복원하지 않는다.

## 발신자 계약

| sender_type | sender_id | 의미 |
| --- | --- | --- |
| USER | 유저 ID (필수) | 인증된 참가자의 메시지 |
| SYSTEM | null (필수) | 연결할 유저 계정이 없는 서버 안내 |

도메인 생성자와 DB의 `sender_type NOT NULL` + `ck_chat_messages_sender`가 조합을 강제한다.
`users.role`과 무관하다. 발신자 유형은 클라이언트 입력으로 받지 않는다.
유저 STOMP 입력은 기존 Principal/참여 검사로 유저 ID를 결정한다.
SYSTEM은 인가된 admin 편성을 app 실행기가 처리할 때만 생성한다.
시스템 유저·`CHAT_SYSTEM_USER_ID`·대체 숫자 ID는 필요하지 않다.

REST `/api/v1/chat-rooms/{id}/messages`와 STOMP `/sub/chat-rooms/{id}`는 같은 계약이다.
시스템 안내는 `sender_type: "SYSTEM"`, `sender_id: null`, `sender_name: "하루"`,
`message_type: "TEXT"`로 내려간다. `message_type`은 내용 유형만 표현하며 `ADMIN`은 저장하지 않는다.
메시지 ID·방 ID·본문·작성 시각·편성 ID·인용 스냅샷·삭제 상태는 DB에 남는다.
시스템 안내에 답장하면 프로필 조회 없이 '하루'를 인용한다. 반응 토글은 활성 참가자에게 허용하지만,
반응자 명단은 메시지 소유자 전용이므로 SYSTEM 메시지의 명단을 일반 유저에게 공개하지 않는다.
차단 발신자 제외/안읽음 집계는 null 발신자를 포함하고, 삭제된 마지막 메시지는 삭제 문구로 표시한다.
admin 참가자 활동 집계에서는 `sender_type=USER`만 센다.

## 편성·실행 책임과 저장 보장

- admin: 기존 SUPER/OPERATOR 권한 확인 → `AdminRoomScheduleService` → core `RoomScheduleService`.
  내용·대상 방·예약 시각을 저장하며 `created_by`/`updated_by`의 `admin:<id>` 감사 정보를 유지한다.
- core: 편성 내용 검증, SCHEDULED에서만 수정·취소·즉시 실행 요청 허용, 조건부 상태 전이,
  메시지 저장과 SENT 처리의 트랜잭션을 담당한다.
- app: `RoomScheduleDispatchScheduler`가 커밋된 DB 예약을 매분 조회한다. admin이 중단되어도 실행 가능하다.
  즉시 실행은 `run_at=현재 시각`으로 변경해 다음 틱에 실행한다. 정상 상태 목표 지연은 다음 1분 틱
  + 배치/DB 처리 시간이다. 처리량이 batch-size를 넘으면 다음 틱으로 밀릴 수 있다.
- app의 AFTER_COMMIT 리스너가 STOMP를 방송하고 기존 Redis fanout으로 다른 app 인스턴스에 전달한다.
  admin → Redis → app 요청 신호나 새 아웃박스는 도입하지 않는다.

실행은 SCHEDULED → SENDING claim을 먼저 커밋한다. claim 시 상태와 `run_at`을 재확인하므로
먼저 커밋된 취소/예약 변경이 우선한다. claim 후 수정·취소는 기존대로 409다.
조회 스냅샷으로 만료/실패를 판정할 때도 상태·예약 시각·수정 시각이 같은 행만 변경한다.
조회 뒤 갱신된 예약은 다음 틱에서 최신 값으로 판단한다.
전송 트랜잭션은 방 → 편성 → 채팅방 순서로 잠근다. 방의 최신 시각/취소 상태와 편성 상태를 다시 확인하고,
메시지 저장 + SENT/메시지 ID 반영을 함께 커밋한다. 취소/종료가 먼저 반영된 방에는 발송하지 않는다.
`uq_chat_messages_schedule_item`이 편성당 메시지 1개를 DB에서도 강제한다(삭제된 행 포함).
기존 `(chat_room_id, sender_id, client_request_id)` 키는 USER 영상 답장의 멱등성에만 사용한다.

예약 시각과 DB의 LocalDateTime은 기존 KST 정책을 따른다. 방 시작 전과 그룹 채팅방 생성 전에는 기다린다.
프로필 공개는 기존처럼 취소되지 않은 편성의 항목별 최초 `run_at`을 기준으로 하며,
잡 성공 여부와 별개다. 이 정책을 메시지 커밋 시점으로 변경하지 않는다.

## 실패·재처리·푸시 정책

| 상황 | 결과/복구 |
| --- | --- |
| claim 전 프로세스 종료 | SCHEDULED 유지, 다음 app 틱에서 조회 |
| claim 후 저장 전 재시작 | SENDING 유지, 기본 5분 후 STUCK으로 최종 실패 |
| 메시지 저장/상태 반영 실패 | 해당 트랜잭션 롤백, 방송 없음, FAILED 기록; 자동 재전송 없음 |
| 미처리 예약 만료 | `max(run_at, 방 시작, updated_at)` 이후 기본 30분이면 EXPIRED |
| 종료·취소된 방 | ROOM_ENDED로 최종 실패 |
| 커밋 후 STOMP/Redis 장애 | 메시지와 SENT 보존, 재입장/재시작 REST 조회로 복원 |
| 커밋 후 푸시 실패/프로세스 종료 | 메시지 재생성 없음. 푸시 재시도 큐는 없으므로 푸시 누락 가능 |
| 운영 글 삭제 | DB `admin_deleted_at` 먼저 반영, app이 다음 틱에 삭제 이벤트 방송 |

푸시는 claim 승자만 커밋 후 요청한다. 같은 편성의 다음 틱/동시 인스턴스에서 다시 보내지 않는다.
같은 항목을 앞선 편성에서 이미 공개했다면 PROFILE_REVEAL 푸시를 중복 요청하지 않는다.
푸시 전송의 exactly-once는 보장하지 않는다. 기존 알림함/야간 제한 정책을 유지한다.
삭제 이벤트의 전송 표시도 커밋 후 전달 실패를 자동 재시도하지 않으므로 재조회가 복구 경로다.
FAILED/STUCK은 운영자가 원인을 확인하고 새 편성을 등록한다. SENT를 SCHEDULED로 직접 되돌리지 않는다.
서버 장애로 30분 이상 밀린 과거 예약을 무조건 재생하지 않으며, 필요하면 운영자가 새 시각을 정한다.

## 프런트 전환과 배포 순서

현재 FE `src/features/chat/types.ts`는 REST/STOMP에서 숫자 `sender_id`를 요구하고,
운영 UI는 `message_type=ADMIN`을 사용한다. **이 백엔드를 구버전 클라이언트에 바로 배포하면 안 된다.**
백엔드가 가짜 숫자 ID로 구버전을 흉내 내는 호환 계층은 두지 않는다.
프런트 수정은 별도 저장소에서 진행하며 아래 완료 증거가 배포 선행 조건이다.

1. FE 선배포: `sender_type`과 nullable `sender_id`를 받는 파서/타입을 REST와 STOMP에 같이 적용한다.
   전환 기간에는 구서버 응답에 `sender_type`이 없을 때에만 `message_type=ADMIN → SYSTEM`, 그 외 USER로
   정규화한다. 새 계약의 SYSTEM 표시/프로필 접근 금지/답장/반응/차단/캐시 복원 테스트를 통과시킨다.
2. 전환 기간은 이 FE 선배포부터 모든 지원 클라이언트의 전환 완료까지다. 버전/세션 확인 없이 날짜로 끝내지 않는다.
   구버전 앱은 업데이트를 요구하고 웹의 캐시·서비스워커·열린 탭을 갱신한다.
   실제 강제 갱신/최소 버전 제어 수단과 지원 버전 목록을 FE 담당자가 확인해야 한다.
   기존 STOMP 연결은 점검 시간에 종료하고 새 클라이언트만 재접속시킨다.
   이 저장소에는 그 최소 버전 차단 장치가 없으므로, 구클라이언트 재접속을 막을 수 없다면 전환을 보류한다.
3. 전환 창에서 app/admin 구버전의 DB 쓰기를 모두 중지한다. preflight → 백업 → up → verify를 실행한다.
   새 app/admin을 함께 기동한다. 구/신버전 혼합 롤링 배포는 지원하지 않는다(새 NOT NULL 계약 때문).
4. 실제 DB 재조회와 신규 SYSTEM 방송/삭제/일반 USER 전송을 확인한 뒤 트래픽과 예약 실행을 재개한다.
   기존 `CHAT_ADMIN_MESSAGE_ENABLED` 플래그를 끄는 것만으로는 과거 SYSTEM 조회를 숨길 수 없다.
5. 모든 지원 클라이언트와 캐시가 새 계약이고 구서버 복귀 기간이 끝나면 FE의 `ADMIN` 입력 호환 분기를 제거한다.

FE 선배포·구버전 차단·실제 배포 환경 검증은 이 백엔드 작업만으로 완료되지 않는다.
전환 승인 시 프런트 릴리스, 구버전 접근 차단 증거, 대상 DB preflight 결과를 함께 기록한다.

## 데이터 이관과 롤백

배포 대상 DB에 대한 변경은 개발 테스트와 별개다. 운영 DB에는 이 문서의 절차를 자동 실행하지 않는다.

1. `scripts/sql/484-chat-sender-preflight.sql`로 실제 컬럼의 null/default, CHECK/FK/유일성,
   메시지 유형별 분포, 편성 ID 중복, 존재하지 않는 유저 참조를 확인한다.
   예상 밖 유형/고아 참조/중복은 의미를 조사해 해결한 후 진행한다. 특정 유저 ID만으로 SYSTEM을 분류하지 않는다.
2. DB 스냅샷/복구 시점을 확보하고 `484-chat-sender-up.sql`을 실행한다.
   원본 `id/sender_id/message_type`는 `chat_message_sender_backup_484`에 보존한다.
   ACCESS EXCLUSIVE 잠금 동안 조회/쓰기가 대기하므로 점검 창을 잡는다.
   모든 기존 ADMIN(삭제 행 포함)은 SYSTEM/null/TEXT, 나머지 TEXT는 USER/원래 ID/TEXT로 변환한다.
   내용·시각·편성 연결·답장·반응·과거 미션 메타데이터·삭제 컬럼은 수정하지 않는다.
3. `484-chat-sender-verify.sql`의 invalid/mismatched/duplicated 결과가 모두 0인지 확인한다.
   기존 ID로 REST에서 본문·인용·반응·삭제를 확인하고 재입장·다른 기기 재조회도 확인한다.
4. 새 SYSTEM 생성 전에는 쓰기를 중단한 채 `484-chat-sender-down.sql`로 원래 발신자/ADMIN을 복구할 수 있다.
   새 SYSTEM이 이미 있으면 down은 실패하도록 설계했다. 무손실 숫자 발신자를 복구할 수 없으므로
   새 버전 수정 배포를 우선한다. 운영 메시지 삭제나 임의 계정 삽입으로 롤백을 강행하지 않는다.
   시스템 유저 계정도 다른 참조를 조사하지 않고 삭제하지 않는다.
5. 롤백용 테이블은 검증/보존 기간 종료 후 별도 변경으로 정리한다. up 재실행은 백업 덮어쓰기를 막기 위해 실패한다.

## 후속 미션의 경계

미션 내용·대상·수행 기간·진행·완료 상태는 별도 DB 도메인으로 정의한다. admin은 편성/관리,
app은 참가자의 수행 요청/실행, core는 상태 전이/판정/저장을 담당한다.
안내 메시지는 필요한 미션 ID/데이터를 영속 참조하되 수행 가능 여부나 완료를 판정하는 원장이 아니다.
향후 안내 메시지 삭제와 미션 취소는 별도 업무 동작이다. 이번 운영 글 삭제 동작은 그대로 유지한다.
`MISSION` 내용 유형, 신규 수행 API, 자동 배정/활동 집계 엔진은 이번 범위에 포함하지 않는다.

## 개발 검증 기록 (2026-10-06)

`refactor/484` 작업 트리, 기준 `719d4b6`, Java 21/로컬 Docker PostgreSQL 16에서 검증했다.
`./gradlew test jacocoTestReport` 성공: core 35 + app 1,024 + admin 110 = 1,169개,
실패·오류·스킵 0. 이후 변경은 이 문서와 주석 정리에 한정되며 동작 변경은 없다.
JaCoCo는 기존 모듈별 보고서를 생성했다. core 보고서는 core 자체 테스트만 집계하므로
app에서 실행하는 core 테스트까지 합친 통합 커버리지 수치로 해석하지 않는다.

- `ChatMessageSenderTest`: 계정 없는 USER/잘못된 SYSTEM 조합 거절, 발신자 소유권.
- `ChatSenderMigrationPostgresTest`: 실제 up/verify/down SQL, 기존 FK·내용·답장·반응·삭제·미션 참조 보존,
  DB CHECK/NOT NULL/유일성 강제, 신규 SYSTEM이 있는 롤백 거절.
- `RoomScheduleDispatchIntegrationTest`: PostgreSQL 동시 claim, 재실행, 갱신/취소 경합,
  저장 실패 롤백·방송 실패 후 재조회, 프로필 공개·푸시 회귀, 실제 임의 포트 HTTP 서버 조회,
  위조 sender 필드 무시, 시스템 안내의 안읽음/미리보기/답장/삭제.
- 기존 STOMP 참여·구독/위조 방지 테스트, Redis fanout 직렬화 및 어드민 권한/활동 집계 테스트 통과.
- red/green 확인: 시스템 유저 없는 기동, USER 발신자 누락, claim 후 방 취소, due 조회 후 예약 갱신.
- Java 전문 리뷰의 오래된 만료 판정 경합을 수정·재검토했고 보안 리뷰도 통과했다.

실제 배포 환경 DB preflight/이관, FE 수정/배포와 구버전 접근 차단, 실제 기기 전환 검증은 미실행이다.
개발 검증 통과를 운영 전환 완료로 간주하지 않는다.

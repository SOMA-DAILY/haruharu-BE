# GROUP 채팅 종료 (#502)

GROUP 실시간 이용 여부는 `chat_rooms.close_at`으로 판단한다. `NULL`이면 열림, 값이 있으면 종료다. 이 값은 **종료를 실제로 확정한 시각**이며 예정 종료 시각인 `rooms.ends_at`과 다르다. 예정 시각 이후 스케줄러가 종료를 저장하기까지의 지연은 허용한다.

## 결정과 비용

기존 #502 구현은 이미 읽은 `chat_rooms`에 더해 원본 `rooms`의 상태와 일정을 조회했다. 변경 후 공통 `ChatRoomLifecyclePolicy`는 전달받은 채팅방의 `closeAt`만 검사한다. GROUP 텍스트 전송과 전달의 추가 `rooms` 조회가 사라진다. `chat_rooms` 조회·잠금, 멤버십 검증, 메시지 저장은 계속 필요하다. 상태 캐시나 Redis 조회는 추가하지 않는다.

종료할 때 채팅방 행 UPDATE 1회와 nullable timestamp 저장 공간이 추가된다. PK로 읽은 행의 필드를 검사하므로 `close_at` 인덱스는 추가하지 않는다. 추가 조회 제거는 테스트로 확인하며, 지연 시간 개선율은 같은 조건의 k6 재측정 전에는 주장하지 않는다.

영상 답장의 ACTIVE/SELECTING 시간창과 미션의 방·미션 일정 검증은 별도 업무 규칙으로 유지한다. 이 경로의 원본 방 조회까지 모두 제거한 것은 아니다.

## 종료와 전송의 순서

1. Batch의 `RoomStatusTransitionService` 또는 어드민의 `RoomService.cancel()`이 원본 방 행을 잠그고 ENDED/CANCELLED를 저장한다.
2. 동기 `RoomEndedEvent → GroupChatCloseListener`가 같은 트랜잭션에서 `chat_rooms.close_at`을 갱신한다. 실패하면 원본 방 변경도 롤백한다. 조건부 UPDATE로 최초 종료 시각을 보존한다.
3. 일반 메시지·운영 글·반응·영상 답장은 채팅방 행을 잠근 뒤 최신 상태를 읽고 검사한다. 반응은 공유 잠금으로 반응끼리의 동시 처리와 기존 중복 삽입 처리를 유지하면서 종료 UPDATE와 충돌하게 한다. 종료 UPDATE가 먼저 커밋됐다면 대기 중이던 작성은 거절한다. 작성이 먼저 잠금을 잡았다면 종료 전 작성으로 커밋되고 종료 처리가 뒤따른다.
4. app의 AFTER_COMMIT 리스너가 `ChatRoomClosedEvent`를 로컬/Redis로 전파한다. admin은 STOMP 인프라가 없으므로 app의 주기적 구독 정리가 DB 종료 상태를 감지한다.

원본 방 종료 후 늦게 실행되는 그룹 생성 이벤트는 저장 상태 ENDED/CANCELLED를 확인해 처음부터 닫힌 채팅방을 생성한다. 채팅방·메시지를 삭제하거나 `chat_room_members.left_at`을 채우지 않는다. 기존 참여자는 REST 메시지 이력·상세·읽음 기능을 계속 이용할 수 있다. 별도로 퇴장한 사람의 권한은 복원하지 않는다. DIRECT는 기존 삭제·참여자 정책을 유지한다.

## STOMP 검사 지점

| 지점 | 처리 |
| --- | --- |
| CONNECT | JWT 인증 후 세션 사용자 설정 |
| SUBSCRIBE·SEND | 인증·활성 멤버십 확인 후 채팅방 종료 확인. 닫혔으면 해당 요청만 거절 |
| broker 구독 등록 | 대기 중 종료와 경합하지 않도록 등록/정리 잠금 안에서 재확인 |
| 메시지·반응 저장 | 채팅방 잠금 후 종료 확인. 영상의 동일 request_id 재시도는 기존 응답만 반환 가능 |
| 미션 메시지 저장 | 기존 미션 일정 검증에 더해 잠금 후 close_at 검사 |
| 커밋 후 전달·Redis 수신 | `ChatDeliveryService`가 채팅방 종료를 확인 |
| WebSocket outbound 직전 | 대기하던 프레임도 재확인. DB 조회 실패 시 전달하지 않음 |
| 서버별 구독 정리 | 등록된 방의 close_at을 확인하여 종료 안내 후 해당 방 구독 제거 |

UNSUBSCRIBE 자체는 SEND를 막지 않으므로 서버의 작성 검사는 유지한다. 이미 네트워크로 전달된 메시지를 회수하는 보장은 없다. 종료 안내는 서버 내부 헤더로 outbound 검사를 통과하며 클라이언트 native header와 구분한다.

## 스케줄과 복구

| 작업 | 기본값 |
| --- | --- |
| 방 상태 Batch | `ROOM_RECONCILE_CRON=45 * * * * *` — 매분 45초, Asia/Seoul |
| 각 app의 구독 정리 | `CHAT_SUBSCRIPTION_RECONCILE_DELAY_MS=10000` — fixed delay |

정상 상황에서 종료 예정 시각부터 다음 Batch까지 최대 약 1분 + 실행 지연이 생길 수 있다. `close_at` 커밋 후 작성·새 구독·전달은 차단되며, 종료 이벤트 유실 또는 admin 취소의 구독 제거는 다음 app 정리 주기에 수행한다. 10초는 고정 보장 시간이 아니며 처리·스케줄러 지연이 더해진다.

Batch가 멈추면 아직 `close_at=NULL`인 방은 계속 열려 있다. 재시작 후 미처리 상태 전이를 수행하면서 닫힌다. 반대로 이미 저장된 `close_at`은 서버 재시작이나 Redis 장애로 유실되지 않는다. app의 구독 정리는 `close_at`을 새로 채우지 않는다. 과거 ENDED/CANCELLED 데이터는 배치 대상에서 제외될 수 있으므로 아래 보정이 필수다.

Redis는 `ROOM_CLOSED` envelope로 다른 app의 구독도 정리한다. 일회성 이벤트가 유실돼도 DB 상태를 주기적으로 다시 확인한다. Batch 실행 식별자는 분 단위 `bucket`이므로 분당 여러 번 실행하려면 식별 단위도 바꿔야 한다.

## 클라이언트 계약

```json
{"type":"CHAT_ROOM_CLOSED","chat_room_id":123}
```

- 해당 방의 `/sub/chat-rooms/{id}` 및 `/sub/chat-rooms/{id}/events`로 안내한 뒤 서버 구독을 제거한다.
- app 종료 이벤트는 기존 `/user/queue/chat-room-events`에도 전달한다. 종료 후 SUBSCRIBE/SEND 거절 안내는 이 개인 큐의 요청 세션에 보낸다.
- 클라이언트는 두 방 토픽과 개인 큐에서 종료 type을 처리하고, 해당 방 입력을 비활성화한다. 중복 안내는 방 ID 기준으로 처리한다.
- 공유 WebSocket과 다른 방·개인 큐는 유지한다. 종료 방은 재접속해도 구독할 수 없으며 REST로 과거 대화를 읽는다.

## 기존 DB 전환 절차

이 변경은 운영에 적용하지 않았다. DDL·보정은 아래 SQL을 검토한 후 별도 적용한다. 구버전은 close_at 없이 종료할 수 있으므로 **이 구현만으로 구버전/신버전을 섞은 무중단 전환이 안전하지는 않다.**

1. [컬럼 추가](../../scripts/sql/502-chat-close-at-up.sql)를 먼저 적용한다. nullable/default 없는 컬럼으로 구버전과 호환된다. 짧은 테이블 잠금은 필요하며 lock timeout이면 재시도한다.
2. 최종 전환 동안 모든 구버전 app/admin의 쓰기와 Batch를 멈춘다. 살아 있는 구버전 writer가 없어야 한다.
3. [기존 데이터 보정](../../scripts/sql/502-chat-close-at-backfill.sql)을 `UPDATE 0`까지 반복한다. 1회 최대 1,000행이며 ENDED/CANCELLED 또는 원본이 삭제/유실된 GROUP을 닫는다. 기존 close_at, DIRECT, 삭제된 채팅방은 건드리지 않는다. 과거 정확한 종료 시각을 추정하지 않고 보정 시각을 기록한다.
4. [검증 SQL](../../scripts/sql/502-chat-close-at-verify.sql)을 통과시킨다. 미보정 종료 방이나 열린 PENDING 등 비정상 원본 상태가 있으면 전환을 중단하고 데이터를 확인한다. 예정 시각만 지난 ACTIVE/SELECTING은 합의한 지연 정책에 따라 배치가 처리한다.
5. 새 app/admin을 기동하고 Batch·쓰기를 재개한다. 종료 방 거절, 활성 방 전송, 이력 조회를 확인한다.

psql에서는 `-v ON_ERROR_STOP=1`을 사용한다. 각 보정 호출은 자체 트랜잭션이다. 중단 없이 전환해야 한다면 먼저 구판정 유지 + close_at 동시 쓰기 릴리스를 거치는 별도 단계가 필요하다. 롤백 시 새 writer를 멈추고 구버전으로 복귀하되 nullable 컬럼은 남겨도 된다. 다시 전환할 때는 보정/검증을 반복한다.

## 검증

- PostgreSQL + 실제 WebSocket/HTTP: 구독 제거, 종료 후 재구독·작성 거절, 다른 구독 유지, 과거 대화 조회.
- PostgreSQL 경합: 일반 메시지·반응·영상 답장이 종료 잠금을 기다린 뒤 최신 close_at으로 거절되는지 확인.
- app 종료 및 실제 admin 컨텍스트 취소: 원본 상태/close_at 동시 커밋·롤백, 중복 종료 멱등성.
- SQL 캡처: 일반 GROUP 전송과 동기 AFTER_COMMIT 전달 경로에서 `rooms` SELECT가 없는지 확인.
- 실제 PostgreSQL에서 배포 SQL 실행: 보정 대상·기존 값 보존·멱등성·1,000행 제한·미완료 전환 거절.
- 전체 회귀: `./gradlew test jacocoTestReport`. 부하 재측정 절차는 [로컬 부하 테스트](../../scripts/perf/chat/README.md).

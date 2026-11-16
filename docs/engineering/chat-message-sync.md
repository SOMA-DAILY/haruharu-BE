# 채팅 메시지 누락 복구 (#562)

기존 `message_id`를 기준으로 즉시 표시한 메시지를 정렬하고, DB의 확정된 범위를 다시 조회해 신규 메시지 누락을 복구한다. 실시간 도착 순서가 달라도 같은 열람 범위의 최종 목록은 같은 순서로 수렴한다. 앞 메시지가 도착할 때까지 화면을 막는 방식은 아니다.

## API

```http
GET /api/v1/chat-rooms/{chatRoomId}/messages/sync?after_message_id=100&size=2
Authorization: Bearer <access_token>
```

| 파라미터 | 계약 |
| --- | --- |
| `after_message_id` | 이미 로컬에 적용한 구간의 끝, 제외 경계. 기본 0 |
| `until_message_id` | 이전 응답의 `sync_target_message_id`, 포함 경계. 첫 요청은 생략 |
| `size` | 기본 30. 기존 이력 API와 같이 1~100으로 제한 |

```json
{
  "isSuccess": true,
  "data": {
    "messages": [
      { "message_id": 101, "content": "안녕하세요" },
      { "message_id": 105, "content": "반가워요" }
    ],
    "sync_target_message_id": 120,
    "next_after_message_id": 105,
    "has_more": true
  }
}
```

메시지 필드는 예시에서 축약했다. 실제로는 기존 `MessagePageResponse.MessageItem`과 같은 발신자·반응·인용·미션·사진 필드를 반환한다. 후속 요청은 `after_message_id=105&until_message_id=120`이다.

- 서버는 권한 검사 후 방에 커밋된 최신 ID를 먼저 읽는다. 첫 요청의 목표는 이 값이며, 이후 요청은 받은 목표를 그대로 사용한다.
- `(after, target]`를 ID 오름차순으로 최대 `size + 1`개 읽는다. 초과 1개는 다음 페이지 존재 여부에만 사용한다.
- 중간 페이지의 다음 위치는 반환한 마지막 메시지 ID다. 마지막 페이지는 목표까지 확인했으므로 `next_after_message_id=target`, `has_more=false`다. 숨김·삭제로 반환할 메시지가 없어도 확인한 범위는 전진한다.
- 동기화 도중 새 메시지가 생겨도 이번 목표는 변하지 않는다. 이번 작업을 끝낸 뒤 새 목표로 다음 동기화를 시작한다.
- 음수·역전된 범위, 해당 방의 현재 커밋된 head보다 큰 after/until은 **400 `CHAT_SYNC_CURSOR_INVALID`**다. head는 삭제·퇴장 숨김 행도 포함하므로 정상적인 소프트 삭제로 커서가 무효화되지는 않는다.
- 빈 방의 목표와 다음 위치는 0이다. 최신까지 적용한 상태면 빈 목록으로 완료한다.
- 매 요청에서 방 존재·활성 멤버십을 확인하고 DIRECT·PAIR의 상대 차단·탈퇴 정책을 적용한다. 종료된 GROUP의 과거 열람은 기존 이력 API와 같다. 인증·권한은 커서로 대체되지 않는다.
- 일반 삭제·퇴장 숨김은 기존 JPA 필터로 빠지고, 운영자 삭제는 기존 '삭제된 메시지' 표시로 반환된다. GROUP 차단 정책도 기존 이력 응답과 동일하다(사진·인용 열람 제한 포함). 새로운 전체 본문 필터를 만들지 않는다.
- 기존 `GET .../messages?cursor=`는 최신순 과거 조회 그대로다. 이 API의 커서와 혼용하지 않는다.

## FE의 저장과 커서

로컬 키는 **계정 + 설치/기기 + 채팅방**이다. 서로 다른 기기의 캐시 상태를 공유 커서 하나로 표현하지 않는다.

| 값 | 의미 | 갱신 주체 |
| --- | --- | --- |
| 수신 최대 ID | 실시간으로 그 ID를 받은 적이 있음 | FE의 수신 상태 |
| `last_synced_message_id` | 복구 응답으로 확인한 구간을 로컬에 적용함 | FE의 메시지 저장과 함께 |
| `last_read_message_id` | 사용자가 열람한 범위 | 기존 읽음 API |

예: 동기화 위치가 100인데 120을 실시간으로 받으면 즉시 표시하되 위치는 100에 둔다. 복구 첫 페이지 101·105를 저장한 뒤 105로 전진하고, 후속 페이지를 모두 적용한 뒤 120으로 전진한다. ID는 모든 방이 공유하므로 101 다음 105가 와도 숫자 공백만으로 누락이라고 판단하지 않는다.

권장 흐름:

1. WebSocket 연결 및 방 구독 준비 후 로컬 동기화 위치로 첫 요청을 보낸다.
2. 응답의 메시지를 ID 기준으로 병합하고 오름차순 표시한다. 실시간으로 먼저 받은 큰 ID 때문에 늦게 온 작은 ID를 버리지 않는다.
3. 메시지 저장과 `last_synced_message_id=next_after_message_id` 갱신을 같은 로컬 DB 트랜잭션으로 커밋한다. 원자적 저장이 어렵다면 메시지 저장을 먼저 완료하고 커서를 나중에 갱신한다.
4. `has_more=true`면 목표를 고정하고 다음 페이지를 조회한다. 방별 복구 요청은 순차 처리한다.
5. 통신·로컬 적용 실패 시 이전 커서로 재시도한다. 중복 ID는 새 말풍선으로 추가하지 않는다. 같은 ID의 상태를 갱신할 때는 늦은 실시간 생성 이벤트가 삭제 등 더 최신 상태를 덮어쓰지 않게 한다.

앱 종료·로그아웃·계정 전환·캐시 초기화 시 데이터와 커서의 수명을 일치시킨다. 새 기기/빈 캐시의 기본 초기 동기화는 0부터다. 최신 일부만 로딩해 기준점을 잡는 bootstrap은 별도 계약이 필요하다. 캐시에서 일부 메시지를 제거했으면 그 범위를 완전히 보관한다고 간주하지 않고 과거 조회로 다시 로딩한다.

읽음 처리와 동기화 완료는 별개다. 뒤 메시지를 먼저 받았다고 미확인 구간까지 읽음으로 올리지 않는다. 복구가 완료돼도 실제로 열람하지 않은 메시지를 자동으로 읽음 처리하지 않는다.

## 호출 시점과 부하

채팅방 진입, WebSocket 재연결, 포그라운드 복귀 때 복구한다. **WebSocket이 살아 있는 동안에도 Redis 구간에서 메시지를 놓칠 수 있으므로 활성 채팅방에서는 주기적으로 재확인**한다. 한 방의 동시 요청을 합치고, 실패 시 backoff와 jitter를 적용한다. 백그라운드에서 모든 방을 무제한 polling하지 않는다.

주기는 FE 연동에서 복구 지연과 DB 요청 부하를 측정해 정한다. 주기가 짧으면 누락 표시 시간이 줄고 조회 비용이 늘어난다. 백엔드에 주기·스케줄러·기기별 커서 테이블·ACK API를 추가하지 않았다.

FE 인계 시 확인할 시나리오: 역순·중복 병합, 페이지 적용 중 앱 종료, 로컬 저장 실패, 늦은 이전 요청 응답, 두 기기의 서로 다른 커서, 캐시 초기화, 실시간 연결을 유지한 Redis 단절, 큰 ID 수신 후 읽음 처리.

## 저장·동시성 전제

`ChatService.sendMessage`, `ChatPhotoService.send`, `VideoReplyService.send`, `AdminChatMessageService.sendAsSystem`, `MissionDispatchStep.send`는 같은 채팅방 행을 잠근 뒤 IDENTITY ID를 발급하고 메시지를 저장한다. 잠금은 DB 커밋/롤백까지 유지한다. 큰 ID가 커밋된 뒤 같은 방의 작은 ID가 뒤늦게 커밋되는 상황을 막는 전제다. 새 저장 경로도 이 규칙을 따라야 한다.

복구는 현재 단일 쓰기 datasource에서 커밋된 행을 조회한다. `@Transactional(readOnly=true)`는 이 저장소에서 별도의 replica 라우팅을 하지 않는다. 향후 읽기 replica를 도입하면 이 경로에 같은 가시성 보장이 필요하다. 조회는 방 잠금을 새로 획득하지 않으며 HTTP 페이지 사이에 DB 트랜잭션을 유지하지 않는다.

head는 `chat_messages`의 native `MAX(id)`다. 시퀀스 `last_value`를 사용하지 않으며, soft-delete/퇴장 숨김 행의 **ID만** 포함한다. 내용 조회는 기존 가시성 필터를 유지한다. IDENTITY 시퀀스는 양의 증가·`CACHE 1`·비순환이어야 한다. 캐시를 늘리면 DB 연결별 선할당 ID 때문에 방 잠금만으로 ID 순서를 보장할 수 없다.

운영 적용 전 읽기 전용 확인:

```sql
SELECT seqincrement, seqcache, seqcycle
FROM pg_sequence
WHERE seqrelid = pg_get_serial_sequence('chat_messages', 'id')::regclass;
-- 기대: seqincrement=1, seqcache=1, seqcycle=false
```

과거 ID를 넣는 backfill, 시퀀스 reset/rewind, 물리 삭제로 head를 낮추는 작업은 이 증분 커서 계약과 호환되지 않는다. 필요하면 커서 초기화/재동기화 또는 별도 migration 계약을 먼저 설계한다. 현재 기능은 새 순번·스키마·인덱스를 추가하지 않는다.

## 범위와 검증

이 API는 **새 메시지 생성의 누락**을 복구한다. 커서 이전 메시지의 삭제·반응 수정·열람 정책 변경 전체를 동기화하는 변경 로그는 아니다. 재진입 시 표시 구간의 상태를 다시 조회하거나 별도 변경 이력 API가 필요하다. 범위 상한이 고정됐다는 것은 메시지 내용 전체가 불변 스냅샷이라는 뜻이 아니다.

`ChatMessageSyncPostgresIntegrationTest`는 실제 HTTP, PostgreSQL 페이지·권한·숨김·롤백·커밋 경쟁·실행 계획을 검사한다. `ChatMessageSyncWritePathPostgresTest`는 사진·영상 답장·운영 안내·미션 각각을 텍스트 전송과 양방향으로 조합해, 실제 저장 경로의 커밋 대기 중 상한 유지와 커밋 후 전체 복구를 검증한다. 미션은 실제 `REQUIRES_NEW` 내부의 커밋을 지연시킨다. `ChatMessageSyncFanoutIntegrationTest`는 동일 PostgreSQL/Valkey에 연결한 두 app 컨텍스트와 두 실제 STOMP 클라이언트로 지연·누락과 HTTP 복구를 검증한다. FE 테스트용 병합 모델은 API 계약 증거이며 앱 구현 완료를 의미하지 않는다.

```sh
./gradlew :core:test --tests '*ChatMessageReaderSyncTest' \
  :app:test --tests '*ChatMessageSyncPostgresIntegrationTest' \
  --tests '*ChatMessageSyncWritePathPostgresTest' --tests '*ChatMessageSyncFanoutIntegrationTest'
./gradlew test jacocoTestReport
```

관련: #453 팬아웃, #454 이전 prevMessageId/after 제안. 이 구현은 #562 계약만 제공한다. 별도 prevMessageId 또는 경쟁하는 after API를 중복 구현하지 않는다.

실행 명령, 전체 테스트 집계와 로컬 측정 조건은 [검증 기록](chat-message-sync-verification.md)에 남겼다.

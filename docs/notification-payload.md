# 알림 payload · FE 연동 계약 (#201)

알림함 API(`GET /api/v1/users/me/notifications`)의 `payload` 타입별 구조와 딥링크 매핑, 그리고 FE가 의존하는 관련 계약을 정리한다.
`payload`는 `Map<String, String>`이며(값은 전부 문자열), 타입(`type`)별로 아래 키가 들어간다.

## 1. 알림함 노출 타입별 payload · 딥링크

알림함에는 `inboxVisible=true`인 타입만 저장·조회된다. (`NotificationType` 참조)

| type | payload 키 | 권장 딥링크(이동 화면) |
|------|-----------|----------------------|
| `ROOM_MATCHED` | `roomId` | 홈(방 카드) 또는 세션 안내 |
| `ROOM_STARTED` | `roomId` | 오늘 화면 `GET /rooms/{roomId}/today` |
| `FINAL_SELECTION_OPEN` | `roomId` | 최종선택 화면 |
| `MUTUAL_MATCH` | `roomId` | 매칭 결과 `GET /rooms/{roomId}/match-result` |
| `SELECTED_BY_SOMEONE` | `roomId` | 오늘 화면(선택자 비공개 — 결과 화면 아님) |
| `MATCH_CONSOLATION` | `roomId` | 홈(미성사 결과는 홈 `matching.pending_result`로 복원) |
| `REACTION_RECEIVED` | `videoPostId` | 해당 영상 피드 |
| `CHAT` | `chatRoomId` | 1:1 채팅방 `GET /chat-rooms/{chatRoomId}` |
| `COIN_REFUNDED` | (빈 객체 `{}`) | 구름 이용내역 |

- 알림함에 저장되지 않는 타입(`inboxVisible=false`): `SLOT_START`, `SLOT_DEADLINE_SOON`, `PAYMENT_COMPLETED`, `WINBACK` — 푸시로만 발송된다.
- FCM 푸시 data에는 위 payload에 더해 `type` 키(타입명)가 항상 포함된다(`PushSendService`). 알림함 응답은 top-level `type` 필드로 제공한다.
- `title`/`body`의 개수·닉네임 등 가변값(%d·%s)은 발송 시점에 치환 완료된 문자열로 내려간다 — payload에는 없다.
- 딥링크 대상이 사라진 경우(방 ENDED 후 `roomId` 등)의 404 처리는 FE 몫이다. 미성사·발표 후 복원은 홈 `pending_result`를 우선 사용할 것.

## 2. match-result의 chat_room_id 지연 계약

`GET /rooms/{roomId}/match-result` 및 홈 `matching.pending_result`의 `chat_room_id`는
**상호매칭 직후 ~ 채팅방 자동 생성 이벤트(AFTER_COMMIT) 처리 완료 전까지 null일 수 있다.**

- 채팅방은 최종선택 트랜잭션 커밋 후 별도 리스너(`ChatRoomAutoCreateListener`)가 생성한다 — 보통 수 초 내.
- FE 권장 대응: `chat_room_id`가 null이면 "채팅 시작하기"를 채팅 탭 이동으로 대체하거나 재조회한다.
- 이 계약은 의도된 설계다(채팅방 생성 실패가 최종선택을 롤백시키지 않게 하기 위함). null이 사라질 때까지 폴링할 필요는 없다.

## 3. 영상 thumbnail_url 미사용 (2026-07-12 확정)

`POST /rooms/{roomId}/video-posts`의 `thumbnail_url`은 **사용하지 않는다** — FE는 보내지 않고(null),
피드·채팅의 영상 미리보기는 `video_url`(presigned)의 첫 프레임을 클라이언트가 직접 렌더링한다.
응답의 `thumbnail_url`은 null로 내려간다(필드 자체는 하위호환을 위해 유지).

## 4. 그 외 FE가 의존하는 계약 (재확인)

- 매칭 미성사 = `GET /rooms/{roomId}/match-result` **404 (`ROOM_MATCH_404_NOT_FOUND`)** — FE 정상 분기로 사용 중이므로 이 코드 계약을 유지한다.
- 홈 `matching.pending_result`는 결과 발표(마지막 날 22:00)부터 **다음 매칭(새 방 배정) 전까지** 유지된다. "방마다 1회 노출"은 FE 로컬 seen 플래그(room_id 기준) 담당.
- `GET /home`·`GET /rooms/{roomId}/today`의 `now`는 주입 Clock 기준(dev 가상 시계 반영) — 카운트다운 기기 시계 보정용.

# 미션과 채팅 안내 (#486)

기준은 #485의 `d2e400d4cb43fca1e5a3b0a6d264be4bd0f85745`이다. `feat/486`에서 안내형 미션을 이전한다.
#482에서 폐기한 자동 배정·활동 집계 엔진과 과거 `room_missions` 테이블은 복원하거나 재사용하지 않는다.

## 소유권과 확장

미션 내부는 책임별 패키지를 먼저 나누고, 각 패키지 안에서 기존 계층을 유지한다.

```text
core/.../domain/mission/
├── common/
│   ├── business/             # 생성·조회·수정·취소와 유형/게시 계획 조율
│   ├── implement/            # RoomMission, 공통 Definition/Handler 계약, Registry
│   └── storage/              # 미션 저장(유형별 정의는 details JSON 한 칸)
├── types/
│   ├── announcement/
│   │   └── implement/        # 안내형 정의·검증·details 변환
│   ├── profilereveal/
│   │   └── implement/        # 프로필 공개형 정의·공개 시각 규칙·공개 알림
│   ├── recording/
│   │   └── implement/        # 촬영형 정의·검증·details 변환
│   └── pairchat/
│       └── implement/        # 1:1 대화 시간 정의·Handler, 채팅의 대화 시간 포트 구현
└── chat/
    ├── business/             # 게시 계획 관리·발송·메시지 삭제
    ├── implement/            # MissionAnnouncement, 채팅 참조 연결
    └── storage/              # 게시 계획 저장
```

`app/.../domain/mission/common/application`에는 참가자 조회 API를 둔다.
`admin/.../domain/mission`도 같은 분류를 사용한다. `common/application`의 미션 관리 API,
`chat/application`의 채팅 안내 API를 분리하고, 유형별 요청 DTO는
`types/{유형}/application/dto`에 둔다. 방 생성 Request는 `room`에서 공통 미션 Request를 참조한다.
컨트롤러의 파일 위치와 분리는 기존 URL·JSON·권한 계약을 바꾸지 않는다.

이들은 하나의 미션 도메인 안의 책임 구분이다. `common`의 서비스는 공통 생명주기와 최초 안내 생성·취소를
조율하며 `chat`과 협력한다. 공통 Handler 계약은 정의 검증·`details` 변환과 안내 본문 생략 허용 여부만 가진다.
안내 발송 뒤의 유형별 알림은 `chat`의 `MissionAnnouncementNotifier`를 필요한 유형만 구현한다.
공통 core 코드와 채팅 코드는 구체 미션 유형을 직접 참조하지 않고 이 계약들을 사용한다.
유형별 규칙은 `types/{유형}`에 모으며, 유형끼리 직접 참조하지 않는다.

| 구성 | 책임 |
| --- | --- |
| `RoomMission` / `room_mission_instances` | 모든 미션의 공통 ID·방·유형·시작·선택적 마감·취소·감사 기록과 유형별 정의(`details jsonb`) |
| `MissionDefinition` | 유형별 입력·조회 데이터 계약. `AnnouncementMissionDefinition`(본문), `ProfileRevealMissionDefinition`(공개 항목), `RecordingMissionDefinition`(촬영 주제) |
| `MissionTypeHandler` | 유형별 검증과 `details` JSON 변환, 안내 본문 생략 허용 여부. 저장소를 참조하지 않는다 |
| `MissionTypeRegistry` | 유형별 Handler 선택. 현재 `ANNOUNCEMENT`, `PROFILE_REVEAL`, `RECORDING`, `PAIR_CHAT` 등록 |
| `MissionImage` / `MissionImageStore` | 미션에 붙는 선택 사진 한 장(유형 공통)과 업로드·존재 확인·조회 주소 발급 |
| `MissionProfileRevealSchedule` / `ProfileRevealAnnouncementNotifier` | 공개 미션의 시작 시각으로 프로필 항목을 열고, START 안내 때 새로 열리는 항목을 알린다 |
| `MissionAnnouncement` / `mission_announcements` | 한 미션의 개별 게시 문구, 안내 용도, 예약·푸시·발송 상태 |
| `ChatMessage` / `chat_messages` | 게시 당시 본문 스냅샷과 미션·안내 ID, 기존 답장·반응·삭제 기록 |
| `ChatMissionLookup` | 채팅이 소유하는 일괄 조회 포트. 미션 구현이 공용 식별 정보를 제공 |

한 미션에는 안내가 0개 이상이다. 안내 없는 미션은 채팅방 없이 생성·조회할 수 있다.
방 참가자가 조회 대상이며, 채팅 멤버십이나 메시지 전송 여부를 미션 접근 조건으로 삼지 않는다.
현재 유형에는 제출·개인 응답·집계 결과가 없다. `MissionResponse` 테이블이나 범용 실행 엔진을 미리 만들지 않았다.

유형별 정의는 유형마다 테이블을 두지 않고 `room_mission_instances.details`(PostgreSQL `jsonb`)에 저장한다.
`type` 컬럼이 해석 방법을 정하고, Handler가 도메인 정의와 JSON 객체를 서로 변환한다. JSON 키 이름이 저장 계약이다.

| 유형 | `details` |
| --- | --- |
| `ANNOUNCEMENT` | `{"content": "오늘의 대화를 시작해 주세요"}` |
| `PROFILE_REVEAL` | `{"reveal_fields": ["MBTI", "AGE"]}` |
| `RECORDING` | `{"content": "오늘 먹은 점심을 찍어 올려주세요"}` |
| `PAIR_CHAT` | `{}` (유형별 입력 없음. 대화 시간은 미션의 `opens_at`·`closes_at`) |

미션 한 건의 일정과 정의가 같은 행이므로 조인 없이 한 번에 읽고 함께 저장한다. DB는 `details`가 JSON 객체인지만 강제하고,
유형별 모양(본문 500자, 허용 공개 항목)은 Handler 검증이 담당한다. 읽을 때는 모르는 값을 버리고 검증하지 않는다.
키 이름을 바꾸거나 필수 값을 추가하면 기존 행의 데이터 이관이 필요하다.
유형과 무관한 공통 속성인 사진은 같은 `details` 칸의 **예약 키 `image`**에 공통 코드가 저장한다
(`{"content": "...", "image": {"key": "mission-images/…-1080x720.jpg", "width": 1080, "height": 720}}`).
유형별 Handler는 이 키를 모르며 쓰지 않는다. 그래서 사진을 읽는 쪽은 유형을 따지지 않고 `mission.image()`만 본다.
사진이 없는 미션에는 키 자체가 없다.
`MissionAnnouncement`는 어떤 종류의 미션에도 붙일 수 있는 채팅 게시 계획이다. 공통 생성·수정 서비스는
구체 유형 클래스를 모르며, 미션과 최초 게시 계획은 같은 트랜잭션이다.

후속 선택형·영상형은 새 유형 enum 값·DB `type` CHECK 확장, 전용 Definition·Handler와 요청 DTO의 허용 타입 등록을 추가한다.
추가 위치는 `types/memberselection`, `types/video`처럼 유형별로 정한다. 정의를 위한 새 테이블·Entity·Repository는 만들지 않는다.
app은 하위 패키지를 자동 스캔한다. admin은 필요한 빈만 명시적으로 등록하므로 새 유형 추가 시
`AdminRoomDomainConfig`에 Handler를 함께 등록한다(Registry는 모든 유형의 Handler가 없으면 기동을 거부한다).
단위 테스트는 해당 유형과 같은 패키지 경로에 둔다.
예를 들어 선택형 정의에는 질문·선택지·선택 제한을 두며, `RoomMission`과 공통 응답에는 전용 필드를 늘리지 않는다.
참가자별 제출·응답은 정의와 달리 행 단위 제약·집계·동시성 제어가 필요하므로 `details`에 넣지 않고 테이블로 설계한다.
실제 제출/수정/집계와 개인별 `can_respond` 규칙은 해당 유형 구현 시 별도로 설계한다.
동일 유형의 질문 문구 차이는 미션 데이터로 표현한다. 제출 수정·익명성·결과 공개 정책은 후속 이슈에서 결정한다.
채팅의 일반 답장은 `TEXT`이며, 미션에 답장했다는 이유로 자동 제출하지 않는다.

## 생명주기와 공개

- `SCHEDULED`: 미션 시작 전 또는 방 시작 전. `OPEN`: 시작 이후이며 아직 마감되지 않음.
- `CLOSED`: 명시적 마감 또는 방 종료. `CANCELED`: 미션 또는 방 취소. 취소가 상태 표시에서 우선한다.
- `closes_at=null`은 현재 방 종료 시각을 따른다. 명시적 마감도 방 종료를 넘겨 참여를 허용하지 않는다.
  응답의 `closes_at`은 설정값, `effective_closes_at`은 방 종료를 반영한 실제 마감이다.
- 시작 시각은 필수이며, 명시적 마감은 시작보다 늦어야 한다. 기존 편성처럼 방 시작 전·종료 후 일정은 저장할 수 있다.
- 참가자는 `opens_at`과 방 시작이 모두 지난 미션만 본다. 상세에서 숨겨진 미션은 404,
  목록·일괄 조회에서는 제외한다. 비참가자는 403이다. 어드민은 미래 미션도 조회한다.
- 참가자 상세·목록·lookup은 메서드 진입 시각을 한 번 고정한다. 일정과 유형별 정의가 같은 행이라
  한 번의 조회로 함께 읽히므로, 도중에 변경이 커밋되어도 이전 일정에 새 비공개 정의가 섞이지 않는다.
  겹친 요청은 변경 전 행 또는 변경 후 행 중 하나를 통째로 보고, 변경 후 일정이 미래면 숨긴다.
  목록의 DB 비교 시각은 `timestamp(6)` 정밀도로 내림해 시작 직전 시각이 반올림되어 공개되는 것을 막는다.
- `opens_at`에 도달한 정의는 일정과 `details`를 수정할 수 없다. 취소와 추가 안내는 가능하다.
  시작 전 정의 수정은 전체 교체이며, 유형은 변경할 수 없다. 예약 안내보다 늦게 시작하도록 바꿀 수 없다.
- 안내 취소는 그 게시만 취소한다. 미션 취소는 남은 `SCHEDULED` 안내를 취소한다.
  이미 claim한 실행은 미션을 다시 확인해 발송을 거절하며, 실행기에서 `FAILED`가 된다.
- 보낸 안내 삭제는 채팅의 `admin_deleted_at`만 반영한다. 미션·다른 안내·본문 원본은 보존된다.
- 마감·취소 때 별도 공용 상태 이벤트를 보내지 않는다. 방 재진입·포그라운드 복귀·관리 동작 뒤 미션 재조회로 복원한다.

프로필 공개는 `PROFILE_REVEAL` 유형의 미션이 정한다. 항목별 공개 시각은 그 항목을 가진 취소되지 않은
**공개 미션의 최초 `opens_at`**이다. 안내의 성공·실패·취소나 마감과 독립적이다. 방의 공개 미션은
`room_id`와 `type` 컬럼으로 조회하고 `details`는 애플리케이션에서 읽는다(JSON 내부 조건 쿼리 없음).
공개 미션을 모두 취소해도 옛 DAY 규칙으로 되돌아가지 않는다. 공개 미션이 한 번도 없는 방은 기존 규칙을 유지한다.
채팅 없는 공개 미션도 공개 시각을 적용하지만 푸시는 발생하지 않는다. 프로필 푸시는 공개 미션의 첫 START 안내 실행 시,
더 이른 공개 미션이 열지 않은 항목이 있을 때 전달한다. 다른 유형의 미션은 프로필을 열지 않으며,
함께 열어야 하면 같은 시각의 공개 미션을 따로 만든다.

## API 계약

기존 `/api/v1/admin/rooms/{roomId}/schedule`은 폐기했다. 호환 위임 경로는 없다.
방 생성의 `chat_schedule`도 `missions`로 전환하며, 구 필드에 값이 있으면 400으로 거절해 미션이 조용히 유실되지 않게 한다.
방·초대코드·초기 미션 및 안내 생성은 하나의 트랜잭션이다. 초기 목록은 최대 100개이고 서버 기본 미션은 없다.

어드민 기준 경로: `/api/v1/admin/rooms/{roomId}/missions`.

| 메서드·하위 경로 | 동작 |
| --- | --- |
| `GET /` | 미래 포함 미션 목록. `after`, `size`(기본 50, 1~100), `missions`·`next_cursor` |
| `POST /` | 미션 생성. `announcement`를 생략하면 안내 없이 생성 |
| `GET /{missionId}` | 미션 정의·현재 상태 |
| `PUT /{missionId}` | 시작 전 정의 전체 교체. `announcement`는 생략, 마감 null은 방 종료 추종 |
| `POST /{missionId}/cancel` | 미션 취소. 반복 호출도 현재 취소 상태 반환 |
| `POST /api/v1/admin/mission-images` | (방 경로 밖) 미션 사진 업로드. multipart `file` |
| `GET /{missionId}/announcements` | 안내 목록. `after`, `size`, `announcements`·`next_cursor` |
| `POST /{missionId}/announcements` | 추가 안내 생성 |
| `PUT /{missionId}/announcements/{announcementId}` | SCHEDULED 안내 전체 교체. 안내 용도는 변경 불가 |
| `POST /{missionId}/announcements/{announcementId}/send-now` | 열린 미션의 SCHEDULED 안내 시각을 현재로 변경 |
| `POST /{missionId}/announcements/{announcementId}/cancel` | SCHEDULED 안내만 취소 |
| `DELETE /{missionId}/announcements/{announcementId}/message` | SENT 메시지 삭제 표시. 반복 호출 허용 |

쓰기는 SUPER·OPERATOR, 읽기는 기존 인증된 어드민 역할에 허용한다.
미션 목록과 안내 목록은 ID 오름차순 커서다. 시간순 표시는 반환된 시각으로 구성한다.

생성 예시(방 생성에서는 같은 객체들을 `missions` 배열에 넣는다):

```json
{
  "type": "PROFILE_REVEAL",
  "details": {
    "reveal_fields": ["MBTI"]
  },
  "opens_at": "2026-10-10T19:00:00",
  "closes_at": null,
  "announcement": {
    "announcement_type": "START",
    "run_at": "2026-10-10T19:00:00",
    "content": "서로의 MBTI가 공개됐어요",
    "push": true,
    "push_title": null,
    "push_body": null
  }
}
```

`type`이 `details`의 허용 형태를 결정한다. 클래스명 기반 역직렬화를 사용하지 않는다.

| `type` | `details` | 규칙 |
| --- | --- | --- |
| `ANNOUNCEMENT` | `{"content": "..."}` | 본문 필수, 500자 |
| `PROFILE_REVEAL` | `{"reveal_fields": ["MBTI", "INTO_NOW", "AGE", "DEPARTMENT"]}` | 항목 하나 이상. 중복 제거 후 표시 순서로 정렬 |
| `RECORDING` | `{"content": "..."}` | 촬영 주제 필수, 500자 |
| `PAIR_CHAT` | `{}` | 다른 칸이 있으면 400. `closes_at` 필수, 같은 방의 취소되지 않은 `PAIR_CHAT`과 시간이 겹치면 400 `opens_at` |

다른 유형의 필드는 조용히 버리지 않고 400으로 거절한다. `ANNOUNCEMENT`에 비어 있지 않은 `reveal_fields`를 보내면
`details.reveal_fields`, `PROFILE_REVEAL`에 `content`를 보내면 `details.content` 오류다. 공개와 함께 채팅 글을 올리려면
공개 미션의 `announcement.content`에 쓴다. 유형은 생성 후 바꿀 수 없다.
조회 응답도 공통 ID·유형·상태·일정과 유형별 `details`를 반환한다. 본문·공개 항목은 최상위에 중복하지 않는다.
방 생성의 유형별 입력 오류는 `missions[0].details.reveal_fields`처럼 배열 위치를 포함한다.
최초 안내 오류는 단건 미션 생성에서 `announcement.run_at`, 방 생성에서는
`missions[0].announcement.run_at`처럼 실제 입력 경로를 반환한다. 안내를 별도 생성·수정하는 API에서는
`run_at`처럼 해당 요청의 최상위 경로를 사용한다. 본문·푸시·안내 용도 검증도 같은 규칙을 따른다.

미션 PUT이 기존 예약 안내와 충돌하면 **409 `MISSION_ANNOUNCEMENT_CONFLICT`**를 반환한다.
시작을 기존 예약 안내 시각 뒤로 미루는 경우다.
이는 저장된 안내와의 업무 충돌이므로 해당 PUT에 없는 `run_at`·`content` 필드 오류를 반환하지 않는다.
운영자는 안내를 먼저 수정하거나 취소한 뒤 미션 수정을 재시도한다. 요청 자체의 잘못된 일정·상세는
기존처럼 400 `VALIDATION_ERROR`와 실제 요청 필드 경로를 반환한다.

### 미션 사진

미션에는 유형과 무관하게 사진을 한 장 붙일 수 있다(선택). 두 단계로 연결한다.

1. `POST /api/v1/admin/mission-images`에 multipart `file`로 올린다. JPG·PNG, 10MB·2,500만 픽셀 이하이며
   서버가 실제로 디코딩해 선언한 형식과 내용을 확인한다. 응답은 `{key, url, width, height}`다.
   방을 만들기 전에도 올릴 수 있다. 쓰기 권한은 SUPER·OPERATOR.
2. 미션 생성·수정 요청의 최상위 `image_key`에 그 `key`를 넣는다(`details` 밖). 방 생성의 `missions` 원소에서도 같다.
   수정은 전체 교체이므로 `image_key`를 비우면 사진을 뗀다. 시작한 미션의 사진은 정의와 함께 고정된다.

`image_key`는 서버가 지은 `mission-images/{uuid}-{가로}x{세로}.{png|jpg}` 형식이고 실제로 올라간 객체만 받는다.
다른 경로의 키(유저 사진·영상)는 조회하지 않고 400 `image_key`로 거절한다. 이 검증이 없으면 임의 객체의 조회 주소가 노출된다.
크기는 키 이름에서 읽으므로 사진 목록을 DB에 따로 두지 않는다. 미션에 붙이지 않은 업로드는 S3에 남으며 자동 정리는 없다.

응답은 미션 응답과 채팅 메시지의 `mission` 객체에 `image: {key, url, width, height}`로 내려가고 없으면 `null`이다.
`url`은 비공개 버킷의 단기 서명 주소(기본 1시간)로 조회·방송할 때마다 새로 발급한다. 클라이언트는 `url`을 저장하지 않고
이미지 캐시는 `key`로 잡는다. 만료된 뒤에는 메시지 목록이나 미션 상세·lookup을 다시 조회해 새 주소를 받는다.

### 촬영 미션 (#496)

`RECORDING`은 참가자에게 촬영을 요청하는 미션이다. 클라이언트는 미션 응답의 `type`과 채팅 메시지의
`mission.interaction_type`이 `RECORDING`인지로 촬영 미션을 판단해 촬영 진입을 보여준다. 사진(`image`)은 유형 공통
속성이라 촬영 미션에도 있을 수도 없을 수도 있으며, 사진 유무로 촬영 미션을 판단하지 않는다.

서버는 유형을 구분해 주는 것까지만 한다. 촬영한 영상은 기존 영상 업로드로 저장하고 미션과 연결하지 않으므로
`requires_response`는 `false`이고, 누가 미션을 수행했는지 서버는 알지 못한다. 참여 집계·미션 영상 모아 보기·보상이
필요해지면 이 유형 위에 영상과 미션의 연결을 추가한다. 촬영 미션의 안내는 안내형처럼 본문이 필수이고 프로필을 열지 않는다.

### 1:1 대화 시간 (#540)

`PAIR_CHAT`은 운영자가 정한 시간 동안 같은 방 참가자끼리(동성 포함, 나·차단 관계 제외) 1:1 대화(채팅방 유형 `PAIR`)를
열 수 있게 하는 미션이다. 유형별 입력은 없고 미션의 시작·끝이 대화 시간이다. 공통 Handler 계약에 두 규칙을 더했다.
`requiresExplicitClose()`(끝 시각 필수)와 `exclusiveSchedule()`(같은 방 같은 유형끼리 [시작, 끝) 겹침 금지)이며, 공통 서비스가
방 행을 잠근 뒤 생성·수정·방 생성 초기 미션(요청 안의 앞선 미션 포함)에서 확인한다. 다른 유형은 기본값(false)이라 변화가 없다.

채팅은 구체 유형을 모르고 채팅 소유 포트 `PairChatSchedule`(→ `PairChatWindow` OPEN·SCHEDULED·NONE)만 쓴다.
`types/pairchat`의 `PairChatMissionSchedule`이 취소되지 않은 `PAIR_CHAT` 미션을 공통 상태 규칙(`statusAt`·`effectiveClosesAt`)으로
계산해 구현한다. 방 시작 전에 잡힌 시작은 방 시작으로 당기고, `opens_day`는 방 시작일 기준 달력 일차다.
`PAIR` 방은 GROUP 과 같이 `chat_rooms.room_id`로 원본 방을 잇고, 두 사람은 `chat_room_members` 두 행이다. room_id 유일은
GROUP 에만 거는 부분 유니크 `ux_chat_rooms_group_room`(WHERE type='GROUP')이고, room_id 로 GROUP 을 찾는 저장소 조회는
type 까지 건다. 열기는 원본 방 행을 잠근 뒤 같은 두 사람의 PAIR 를 찾고 없을 때만 만들어 동시 열기도 한 방으로 모은다.
메시지 전송은 대화 시간 밖이면 409 `PAIR_CHAT_CLOSED`, 방 종료 뒤엔 기존 `CHAT_ROOM_CLOSED`다. 방이 끝 시각에 끝나거나(app 전이)
어드민이 취소하면 원본 방 트랜잭션 안에서 `PairChatCloseListener`가 그 방의 PAIR 를 닫고 쌍의 두 사람에게 종료를 알린다
(admin 은 `AdminRoomDomainConfig`에 이 리스너를 등록한다. app 전이는 취소된 방을 건너뛴다).
구버전 앱은 채팅 목록을 DIRECT·GROUP 로만 읽으므로 기본 목록에 PAIR 를 넣지 않고 `include_pair=true`일 때만 넣는다.
푸시는 새 type `PAIR_CHAT`(구버전 앱은 모르는 type 이라 눌러도 이동하지 않음)이고 발신자 이름은 방 가명이다.
안내는 다른 유형과 같다(본문 필수). 운영 글 머리말은 `[공지]`다.

안내 용도는 `START`, `REMINDER`, `RESULT`이며, 현재 RESULT는 운영자가 작성한 안내다. 자동 집계를 의미하지 않는다.
안내는 미션 시작보다 빠르게 게시할 수 없다. 방이 열려 있으면 마감 후 RESULT 안내를 추가할 수 있다.
안내 본문은 필수이며, 공개 미션(`PROFILE_REVEAL`)의 START만 본문 없이 허용한다(채팅 글 없이 공개 푸시만 전달).
본문 500자, 푸시 제목 15자, 푸시 본문 30자 제한은 이모지 code point 기준이다.
안내 전체 교체의 null/빈 문자열은 값을 지운다. 기존 PATCH의 null 유지 의미는 사용하지 않는다.

참가자 기준 경로는 `/api/v1/rooms/{roomId}/mission-instances`이다. 폐기한 자동 미션 `/missions` 경로와 구분한다.

- `GET /`: 공개된 미션 커서 목록(`after`, `size`).
- `GET /{missionId}`: 공개된 미션 상세. 현재 유형은 모두 `requires_response=false`, `can_respond=false`.
- `GET /lookup?ids=10,11`: 최대 100개 일괄 보충 조회. 다른 방·숨김·없는 ID는 제외한다.

이 조회들은 메시지를 생성하지 않는다. 공통 상태·시각·`interaction_type`과 유형별 `details`를 반환하고 운영 감사·푸시 설정은 제외한다.
정의가 미션 행에 함께 있으므로 공개 가능한 미션만 선별해 그대로 응답하며 건별 추가 쿼리가 없다.

## 채팅 계약과 실행

REST 기록과 STOMP 방송은 같은 저장 메시지를 표현한다. 기존 USER/SYSTEM 계약은 유지한다.
일반 메시지는 `message_type=TEXT`, `mission=null`이며 일반 시스템 공지도 SYSTEM+TEXT로 표현할 수 있다.
미션 안내는 SYSTEM+MISSION이고 다음 공용 참조를 포함한다.

```json
{
  "sender_type": "SYSTEM",
  "sender_id": null,
  "message_type": "MISSION",
  "content": "서로의 MBTI가 공개됐어요",
  "mission": {
    "mission_id": 10,
    "announcement_id": 101,
    "announcement_type": "START",
    "interaction_type": "PROFILE_REVEAL",
    "image": {
      "key": "mission-images/3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b-1080x720.jpg",
      "url": "https://…서명 주소",
      "width": 1080,
      "height": 720
    }
  }
}
```

`interaction_type`은 미션 유형이다(`ANNOUNCEMENT`, `PROFILE_REVEAL`, `RECORDING`, `PAIR_CHAT`). `image`는 미션에 사진이 있을 때만 값이 있고
REST 기록과 STOMP 방송 모두 추가 조회 없이 말풍선에 그릴 수 있게 서명 주소를 함께 보낸다. 동적 미션 상태와 개인 응답은 공용 STOMP/Redis에 넣지 않는다. 채팅 한 페이지의 안내와 미션을 각각 한 번에 조회해 참조를 조합한다.
삭제된 안내는 기존 삭제 문구·`deleted=true`, `mission=null`이며 인용도 unavailable이다. 사진도 함께 내려가지 않는다.
사용자 입력의 발신자·유형·미션 ID는 공식 메시지 생성에 사용하지 않는다.

`MissionDispatchScheduler`가 유일한 안내 실행기다. 기존 `haruharu.chat.admin-message` 설정과 플래그를 재사용한다.
플래그는 채팅 안내 실행만 제어하며 미션 시간·조회·프로필 공개를 멈추지 않는다.
admin이 중단돼도 app이 DB 계획을 실행한다. app/admin 사이의 직접 호출은 없다.

claim은 `SCHEDULED → SENDING` 조건부 UPDATE를 별도 커밋한다. 실행은 **방 → 미션 → 안내 → 채팅방** 순서로 잠그고,
시간·취소·미션/안내/방·GROUP 채팅 일치를 재검증한다. 메시지 저장과 SENT 반영은 같은 트랜잭션이며 커밋 후 방송한다.
DB는 내용 유형/참조 조합, 안내당 메시지 유일성, 미션/안내/방 복합 FK를 강제한다.
미션 행은 허용 유형 CHECK와 `details`의 NOT NULL·JSON 객체 CHECK를 가진다.
안내 대상 방과 채팅방의 일치는 실행 서비스에서도 검증한다. 삭제된 메시지도 유일성 범위에 포함한다.

기존 복구 정책을 유지한다.

| 상황 | 처리 |
| --- | --- |
| 방 시작 전·GROUP 채팅방 없음 | 다음 틱까지 대기 |
| 미처리 안내 | `max(run_at, 방 시작, updated_at)`으로부터 기본 30분 경과 시 EXPIRED |
| SENDING에서 프로세스 종료 | 기본 5분 후 STUCK 최종 실패 |
| 저장 실패 | 메시지·SENT 롤백, 방송 없음, FAILED. 자동 재전송 없음 |
| 방 종료·취소, 미션 취소 | 발송 거절. 실패 사유 기록 |
| 커밋 후 STOMP/Redis 실패 | 메시지·SENT 보존, REST로 복원 |
| 커밋 후 푸시 실패 | 메시지 재생성 없음. 별도 푸시 재시도 큐가 없어 누락 가능 |

실패한 안내는 원인을 확인해 같은 미션에 새 안내를 추가한다. 이는 미션 재개를 뜻하지 않는다.
취소된 미션에는 새 안내를 추가하지 않으며 새 미션을 개설한다. SENT를 SCHEDULED로 되돌리지 않는다.

## 데이터 전환과 클라이언트 배포

운영 DB에 적용하지 않았다. 아래 SQL은 PostgreSQL 테스트 DB에서 검증하는 배포 산출물이다.

1. #484 발신자 이관이 끝난 상태가 선행 조건이다. 지원 앱의 REST/STOMP 파서·캐시·말풍선이 MISSION과 nullable mission을 처리하고,
   새 상세/lookup을 사용하는지 확인한다. 어드민은 새 missions API·방 생성·PUT 계약으로 함께 전환한다.
   투표 UI/제출 기능은 이번 범위가 아니다. 실제 FE 렌더링·기기 검증은 해당 저장소에서 수행한다.
2. 구클라이언트의 업데이트/최소 버전·캐시 갱신을 확인한다. 서버에는 최소 버전 차단 장치가 추가되지 않았다.
   기존 STOMP 연결도 종료하고 새 계약 클라이언트가 재접속하도록 한다. 미지원 클라이언트가 남으면 전환을 진행하지 않는다.
3. DB 스냅샷과 점검 창을 확보한다. 구 app/admin의 모든 쓰기·예약 실행을 중단한다.
   SENDING은 기존 실행 결과 또는 기존 STUCK 정책으로 먼저 확정한다. 중간 실행을 임의 재예약하지 않는다.
4. `486-mission-preflight.sql` 실행: 고아·방 불일치·미확정 SENDING·모호한 편성/메시지 연결과
   본문·공개 항목이 모두 없는 편성은 차단한다.
   이관 대상은 #457의 명시적 편성과 그 `schedule_item_id` 메시지다. 독립 SYSTEM 공지는 TEXT로 남긴다.
5. `486-mission-up.sql` 실행: 하나의 트랜잭션과 ACCESS EXCLUSIVE 잠금 안에서 기존 한 줄을 미션 하나+START 안내 하나로 복제한다.
   미션 `room_mission_instances`와 게시 계획 `mission_announcements`를 분리한다. 공개 항목이 있던 편성은
   `PROFILE_REVEAL`(`details.reveal_fields`), 나머지는 `ANNOUNCEMENT`(`details.content`)가 된다.
   공개 편성의 채팅 문구는 START 안내의 `content`와 보낸 메시지에 그대로 남는다.
   비어 있는 새 테이블에 기존 ID를 복사하고 identity를 다음 값으로 설정한다. 마감은 null(방 종료 추종), CANCELED만 미션 취소로 매핑한다.
   안내 상태·실패 사유·감사·보낸 메시지·soft delete는 보존한다. 기존 `room_schedule_items`, `room_missions` 등은 변경하지 않는다.
   메시지는 유형과 새 참조만 바꾸며 `schedule_item_id`를 보존한다. 기존 message_type 단일 컬럼 CHECK는 이름·정의를 백업해 교체한다.
   알 수 없는 복합 CHECK는 작업을 중단한다. 원본 편성·메시지와 이관 직후 새 테이블은 `_backup_486` 테이블에 보존한다.
6. 쓰기가 중단된 상태에서 `486-mission-verify.sql` 실행: 일대일 매핑·ID·본문·시각·답장·삭제 등 원본 값 보존을 검증한다.
   새 app/admin을 함께 기동한다. 구 실행기와 새 실행기를 섞는 롤링 배포는 지원하지 않는다.
7. 기존 기록 재조회, 신규 안내 1회 저장·방송, 미션 조회·취소·삭제와 일반 채팅을 확인한 뒤 트래픽·실행을 재개한다.
   플래그를 끄는 것만으로 이미 이관된 MISSION 응답을 구클라이언트에서 숨길 수 없다.
8. **새 런타임 쓰기 전** 문제가 발견되면 쓰기를 멈춘 채 `486-mission-down.sql`을 실행할 수 있다.
   미션(`details` 포함)·안내·편성·채팅에 새 변경이 있으면 자동 롤백을 거부한다. 수동 대조 또는 수정 배포가 필요하다.
   원래 메시지 유형과 CHECK 정의를 복원하고 새 참조·테이블을 제거한다. 백업은 보존하며 up 재실행은 백업 덮어쓰기를 허용하지 않는다.

실제 DB preflight·이관, FE/admin 출시, 지원 클라이언트 확정·구버전 차단, 실제 기기에서의 말풍선 검증은 배포 시 별도로 확인해야 한다.

## 개발 검증 기록 (2026-10-07)

Java 21, 로컬 Docker PostgreSQL 16에서 다음을 확인했다.

- 최신 `develop`의 #490(`94bfd51`)을 반영한 뒤 CI와 같은 `./gradlew build --no-daemon` 통과:
  core 58 + app 1,051 + admin 121 = **1,230개**, 실패·오류·스킵 0. 영상 병합 수정 후 전체 빌드는
  2분 11초, 시각 정밀도 테스트 수정 후 최종 빌드는 1분 19초다. 후자는 변경된 app 테스트를 재실행하고
  동일 상태의 core·admin 검증을 재사용했으며, 패키징과 JaCoCo 보고서도 확인했다.
  develop 반영 전 미션 구현의 `./gradlew test jacocoTestReport`도 1,221개 통과했다.
- PR 병합 결과에서 #482가 제거한 `List`·로거 선언을 #490이 사용해 컴파일이 실패하는 문제를 확인했다.
  같은 병합 상태에서 실패를 재현한 뒤 필요한 선언을 복원했다. 영상 업로드 통합 테스트 23개와
  Java 재리뷰로 대기 세션 조회·교체 및 기존 업로드 동작의 보존을 확인했다.
- Linux CI의 발송 회귀 테스트에서 저장 전 나노초 시각을 재사용해 `timestamp(6)`으로 반올림된 미션
  시작보다 이른 안내를 요청하는 문제를 재현했다. 최초 조회에 담긴 저장된 예약 시각으로 복원하고,
  마이크로초 정렬·내림·올림 입력 3종 모두에서 claim 재검사와 최신 본문 발송을 검증했다.
- 패키지 이동 후 실제 HTTP·어드민 API·발송 회귀 확인: 분리된 컨트롤러의 URL·인가·JSON 계약과
  JPA Entity/Repository 스캔, 명시적 빈 등록이 유지된다. 도메인·Java 리뷰에서도 이동 누락이 없음을 확인했다.

- 참가자와 admin 실제 임의 포트 HTTP: 인증·방 참가자·역할 제한, 미래 미션 비공개, 채팅 없는 조회,
  목록/lookup, 새 방 생성 및 미션 취소, 구 schedule 404.
- 새 `details` JSON: type/details 순서 무관, 허용되지 않은 유형·누락 거부, 중첩 응답,
  여러 행의 잘못된 공개 항목에 배열 인덱스를 붙이는 오류 처리(red 후 green).
- 리뷰 회귀 검증: PostgreSQL에서 일정 수정 커밋을 공통/상세 조회 사이에 배치해 참가자 상세·목록·lookup의
  미래 상세 노출을 재현하고 해결했다. 외부 READ_COMMITTED 호출, 조회 도중 시작 시각 통과,
  시작 1ns 전 및 정확한 시작 시각의 가시성까지 검증했다. 최초 안내 검증 오류 5종의 단건·일괄·별도 안내
  경로도 검증했으며, 수정 후 Java·보안 재리뷰를 통과했다.
- 기존 예약 안내와 미션 수정의 충돌은 실제 HTTP에서 일정 변경·공개 항목 제거 두 사례를 재현한 뒤
  409 업무 오류로 수정했다. 오류 응답의 `fields` 생략, 미션·안내 원본 보존, 안내를 보정한 후 같은 수정의
  성공, 요청 자체의 400 필드 오류 유지를 확인하고 Java 재리뷰를 통과했다.
- PostgreSQL: 공통/상세/최초 안내 저장 도중 DB 제약 실패 시 전체 롤백, 상세 수정 실패 시 공통 일정까지 롤백,
  수동 PK 상세 갱신의 생성 감사값 보존, 공통 또는 상세 soft delete의 공개 조회 제외, 시작 후 규칙 고정.
- 동시 claim, 중복 실행, 예약 갱신/취소/방 종료 경합, 저장 롤백·방송 실패 후 복원,
  프로필 공개·푸시·답장·안읽음·삭제 회귀, 미션 여러 안내와 공개 미션 취소.
- 실제 preflight/up/verify/down SQL: 구 TEXT/ADMIN CHECK 공존, 공통/상세/안내 매핑·감사·본문·삭제·답장·반응 보존,
  복합 FK·유일성·CHECK, SENDING/잘못된 방 연결 차단, 상세만 바뀌어도 롤백 거절.
- 실제 Boot 매퍼·브로커 변환기로 TEXT/MISSION의 Redis fanout 전후 JSON 계약.
- 유형당 한 번의 상세 일괄 조회, 누락 상세 거부. 안내형 도메인 정규화·검증.
- 도메인·Java·보안 전문 리뷰 통과. 유형별 분리 이후 API 오류 경로 지적을 수정해 재검토 통과했고,
  Java 리뷰의 저장 실패 원자성 검증 권고도 실제 DB 실패 테스트로 보강했다.
- `git diff --check` 및 이번 작업의 추적·미추적 파일 점검 통과. 설정·검증 기준 변경과 시크릿 노출 없음.

JaCoCo는 기존 모듈별 보고서를 생성했다. core 보고서는 core 자체 테스트만 집계하므로 app에서 실행한
core 기능 테스트까지 합친 커버리지 수치로 해석하지 않는다. 기존 admin 테스트 컨텍스트의 종료 순서로
Testcontainers 종료 후 DB 연결 경고가 출력됐지만 테스트 실패·오류는 없었다.
FE 코드 수정·기기 UI 확인·운영 DB 적용·배포는 이 검증에 포함하지 않는다.

## 유형별 정의의 JSON 전환 (2026-10-08)

유형마다 상세 테이블을 두던 구조(`announcement_missions`)를 `room_mission_instances.details jsonb`로 바꾸고,
안내형의 부가 필드였던 프로필 공개를 `PROFILE_REVEAL` 유형으로 분리했다. 문서 본문은 전환 후 기준이며,
바로 위 2026-10-07 검증 기록의 유형별 상세 테이블·스냅샷 트랜잭션 서술은 전환 전 구조에 대한 것이다.

- 유형이 늘 때마다 테이블·Entity·Repository·조인·admin 빈 등록이 함께 늘던 비용을 없앴다.
- 참가자 조회의 `REPEATABLE_READ + REQUIRES_NEW`는 공통 행과 상세 행을 따로 읽는 사이의 변경 때문에 필요했다.
  한 행이 되어 제거했고, 조회 중 일정·정의 변경 커밋 시 미래 정의가 노출되지 않음을 PostgreSQL에서 다시 검증했다.
- `details`에 스키마 버전 값은 두지 않았다. 키 변경이 필요해지면 그 이관에서 함께 정한다.
- 미션 사진(2026-10-08): 유형 공통 속성이라 `details`의 예약 키 `image`로 공통 코드가 다룬다. 배포 시 admin 태스크 역할에
  `mission-images/*`의 `s3:PutObject`·`s3:GetObject`, app 태스크 역할에 `s3:GetObject` 권한이 필요하다(미확인).
- 촬영 미션 유형(2026-10-10, #496): `RECORDING` 추가. 유형 값 목록은 DB `type` CHECK에도 있으므로, #496 이전에
  `room_mission_instances`가 만들어진 DB에는 `scripts/sql/496-mission-type-recording-up.sql`을 app/admin 배포 전에 적용한다
  (재실행 가능). 적용하지 않으면 기동은 되지만 촬영 미션 저장이 실패한다. 새로 이관하는 DB는 `486-mission-up.sql`이 같은 목록으로 만든다.
- 1:1 대화 시간(2026-10-23, #540): `PAIR_CHAT` 미션·`PAIR` 채팅방·`PAIR_CHAT` 알림. app·admin 배포 전에
  `scripts/sql/540-pair-chat-up.sql`을 적용한다(재실행 가능, 세 표의 생성된 이름 CHECK 교체 + `uq_chat_rooms_room` 을 GROUP 부분 유니크
  `ux_chat_rooms_group_room`으로 교체). 적용 자체는 옛 서버와 함께 돌아도 안전하다(PAIR 행이 없으면 막는 범위가 같다).
  **PAIR 행이 생긴 뒤에는 #540 이전 이미지를 돌리면 안 된다** — 옛 서버의 room_id 조회는 type 조건이 없어 같은 방 GROUP·PAIR 를
  함께 집고 방 입장·종료·취소가 실패한다. 롤백 하한은 #540 이미지이고, 첫 `PAIR_CHAT` 미션은 옛 task 가 모두 빠진 뒤 만든다. 옛 엔티티는
  `uq_chat_rooms_room`을 선언하고 있어 dev(`ddl-auto: update`)에서 옛 서버가 재시작하면 전체 유니크를 되살린다 — dev 는 새 서버 배포 뒤
  up 을 (다시) 적용한다. SQL 없이 새 서버가 먼저 나가도 `PAIR_CHAT` 미션을 만들기 전까지 동작 변화가 없다. 되돌리기
  `540-pair-chat-down.sql`은 1:1 대화 데이터가 있으면 멈춘다. 확인은 `540-pair-chat-verify.sql`.

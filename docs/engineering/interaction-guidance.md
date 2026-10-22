# 몰아보기 첫 반응·답장 안내 (#541)

기본 OFF. 방 시작 1시간 이후 ACTIVE 기간에 타인 영상 몰아보기에서 안내한다.
반응과 영상 답장을 각각 방 참가자당 한 번 안내하고, 한 몰아보기 세션에서는 한 종류만 확보한다.
24시간 종료·실험 기간·성공 임계값은 미확정이므로 코드에 고정하지 않는다.

## 설정과 적용 순서

1. 별도 운영 승인을 받은 뒤 `scripts/sql/541-interaction-guidance-up.sql`을 먼저 적용한다.
2. 기본 OFF 상태로 API와 앱을 배포하고 두 플랫폼과 계측을 검증한다.
3. 별도로 승인한 신규 방 시작 경계와 비율로 활성화한다. 현재 진행 방을 소급 등록하지 않는다.

| 환경 변수 | 기본값 | 의미 |
| --- | --- | --- |
| `HARUHARU_INTERACTION_GUIDANCE_ENABLED` | `false` | 안내·신규 행동 기록 활성화 |
| `HARUHARU_INTERACTION_GUIDANCE_ENROLLMENT_STARTS_AT` | 없음 | 등록 대상 방 시작 시각의 하한. 켤 때 필수, 서버와 같은 Asia/Seoul ISO 로컬 시각 |
| `HARUHARU_INTERACTION_GUIDANCE_TOOLTIP_PERCENT` | `50` | 방 단위 TOOLTIP 추첨 비율 0~100. QA는 100 사용 가능 |
| `HARUHARU_INTERACTION_GUIDANCE_EXPERIMENT_KEY` | `first-video-interaction-v1` | 실험 이름 |

기존 방 시작 이벤트에서 DB 방 행 잠금 안에서 배정을 한 번 추첨하여 저장한다.
조회/확보 API도 같은 멱등 로직으로 누락을 복구한다. 앱에 방문하지 않은 방도 배정 대상에 포함된다.
같은 방의 참가자는 동일 군이고, 비율·실험 이름 설정을 바꿔도 저장된 배정은 바꾸지 않는다.
OFF 또는 등록 경계 밖 방의 기존 반응·답장 성공 경로는 새 테이블에 접근하지 않는다.
활성화 전에 취소된 반응의 과거를 추측하여 역산하지 않는다.

## API 계약

`GET /api/v1/rooms/{roomId}/interaction-guidance`는 방 참여 권한을 확인하며 안내를 소비하지 않는다.
`experiment_key`, `variant(CONTROL/TOOLTIP/NOT_ENROLLED)`, `assigned_at`, `now`, `eligible_at`,
`first_reacted_at`, `first_video_replied_at`, `reaction_guide_consumed_at`, `reply_guide_consumed_at`,
`next_guide(REACTION/REPLY/null)`을 반환한다.

`POST /api/v1/rooms/{roomId}/interaction-guidance/claim`은
`kind`, `video_post_id`, `viewer_session_id(비어 있지 않은 최대100자)`를 받는다.
`claimed`, `kind`, `claim_id`, `claimed_at`, `reason`을 반환한다.
기존 ApiResponse 래퍼를 사용하며 data 필드명은 snake_case다.

다른 방 영상은404, 방 외 사용자는403. 본인·삭제·숨김·차단·탈퇴·채팅 나감·미래 슬롯 등
실제로 조작하기 어려운 대상은 소비 없이 `VIDEO_UNAVAILABLE`로 거절한다.
한 번에 반응부터 안내하고, 이미 반응했거나 반응 안내를 소비했다면 답장으로 넘어간다.

## 중복과 성공의 정의

- 같은 종류+세션+영상 재시도는 원래 claim ID와 시각을 반환한다. HTTP 유실·StrictMode 재시도에 대응한다.
- 같은 종류를 다른 세션에서 다시 확보하면 `GUIDE_ALREADY_CONSUMED`.
- 한 세션에서 다른 종류를 확보하면 `SESSION_ALREADY_CONSUMED`.
- 같은 세션·종류인데 영상이 달라지면 `REQUEST_MISMATCH`.
- 안내 클릭·입력창 열기는 행동 완료가 아니다. 반응 생성/교체 및 영상 답장 저장 성공과 같은 트랜잭션에서
  최초 행동 시각을 저장한다. 취소·삭제·재시도는 최초 시각을 되돌리거나 갱신하지 않는다.
- 참가자 DB 행 잠금으로 최초 상태 생성·동시 기기·두 행동 동시 성공을 직렬화하고, 종류·세션 유일 제약을 둔다.

claim은 노출 권리를 한 번 소비하는 예약이다. 성공 응답 직후 앱이 종료되면 실제 화면에 보이지 않을 수 있다.
따라서 claim 수를 노출 수로 사용하지 않는다. 실제 보인 노출·종료·탭은 앱이 별도 계측한다.
기수별 방 단위 배정을 유지하고, 실제 노출자만 선별한 전환율은 전체 효과와 구분하여 해석한다.

# 영상 답장 추천 AI (#558)

방 참가자가 다른 사람의 영상을 볼 때, 서버가 영상 장면·캡션·게시 전 방 기록으로 답장 방향 카드(최대 3개)를 만든다.
사용자는 카드를 참고해 직접 답장을 쓴다. 자동 입력·자동 전송은 없다.
기준 명세는 「영상 답장 추천 AI 개발 명세」 v1(2026-11-08)과 개발 패키지(계약·프롬프트·인수 테스트 목록)다.

## 구성

```
앱 ──HTTP──▶ app 서버(Java) ──▶ PostgreSQL ai_reply 스키마 ◀── AI 작업기(Python, ai-worker/, ECS Fargate)
                │                                              │
                └─ 서비스 표(영상·채팅·차단)를 읽어 권한 확인된 스냅샷을 만든다      ├─ GPT-6.1 Terra (OpenAI Responses API)
                                                               ├─ 임베딩: Cloudflare Workers AI @cf/qwen/qwen3-embedding-0.6b
                                                               │   (로컬 개발은 embedding-serve 의 고정 revision)
                                                               └─ 영상: S3 영상 버킷(users/·demo/ 읽기만)
```

- 앱은 모델·임베딩 서버에 직접 접속하지 않는다. 업로드·채팅은 모델 응답을 기다리지 않는다.
- `ai_reply` 스키마는 Hibernate 가 관리하지 않는다. 정의는 `scripts/sql/558-reply-recommendations-up.sql` 하나다.
  Java 는 `JdbcClient` SQL 로, 작업기는 psycopg 로 읽고 쓴다. 시각은 모두 `timestamptz`(UTC)다.
- 작업기 DB 계정은 `ai_reply` 만 쓴다. 서비스 표(영상·채팅·차단)는 app 서버만 읽는다.
  그래서 권한·시점 판단은 app 서버가 하고, 작업기는 app 서버가 만든 스냅샷만 본다.
- 대기열은 명세의 Outbox+SQS 대신 DB 작업 표(`ai_job`)다. 게시 트랜잭션이 `analysis_request` 를 같은 트랜잭션에 넣고(outbox),
  작업기는 `FOR UPDATE SKIP LOCKED` 로 작업을 가져간다. 중복 방지·재시도·lease 규칙은 명세와 같다.

## 흐름

1. **게시**: 영상 게시(`VideoUploadedEvent`, 같은 트랜잭션)에서 `analysis_request(SNAPSHOT_PENDING, origin=PUBLISH)` 를 만든다.
   이 SQL 은 savepoint 안에서만 돌아 실패해도(스키마 미적용·권한·제약 등) 그 부분만 되돌리고 게시는 저장된다.
   기능 스위치가 꺼져 있거나 활성 버전이 없으면 만들지 않는다(이후 추천 요청 때 만든다).
2. **스냅샷**(app 스케줄러, 3초 간격): 대기 요청을 잡아 `analysis_input`(게시 시각 T 이전·T 이전에 이용 가능했던 방 기록)과
   `evidence_source` 를 만들고, `ai_job(ANALYSIS)` 을 넣은 뒤 요청을 `SNAPSHOT_READY` 로 바꾼다.
3. **분석**(작업기): 영상 10·50·90% 장면 3장 + 캡션 + 기록으로 한 번 호출해 `analysis_snapshot` 을 저장하고,
   인용한 출처를 `evidence_source.cited` 로 표시한 뒤 `ai_job(GENERIC)` 을 넣는다.
4. **일반 추천**(작업기): 참고 사례 검색(top 3) → 후보 최대 10개 → 독립 검수 → 서버 규칙으로 최대 3개 →
   `generation_attempt` + `recommendation_set(kind=GENERIC)`. 0개도 정상(`EMPTY`)이다.
5. **추천 요청**(app API): 시청자 권한을 다시 확인하고 `recommendation_request` 를 만들거나 찾는다.
   준비된 일반 세트가 있으면 바로 `READY`/`EMPTY`, 아니면 `PENDING`.
6. **개인화**(스위치 ON 일 때): app 이 `personal_input`(시청자 본인 기록·명시적 답글 관계)을 만들고 `ai_job(PERSONAL)` 을 넣는다.
   작업기는 개인 후보 최대 6개 → 같은 검수 → 엄격한 교체 규칙으로 병합한다. 채택이 없으면 일반 세트 id 를 그대로 쓴다.
7. **노출·답장 연결**(app): 앱 사건으로 `reply_episode` 를 만들고, 저장된 실제 영상 답장을 `reply_link` 로 연결한다.
8. **개선 루프(피드백 배치)**: 운영은 **한 달에 한 번**(매달 1일 04:00 KST, EventBridge Scheduler → ECS 작업
   `reply-ai batch run-scheduled`, 2026-11-09 결정) 지난 실행 뒤 닫힌 episode 로 실제 답장 적절성·방향 포함을 판정하고,
   같은 원인이 3건 이상이면 한 모듈 지시 개선안을 만들어 같은 입력으로 재검증한다. 통과한 버전만 CAS 로 자동 적용한다.
   애매하거나 나빠지면 기존 버전을 둔다. 상시 작업기는 `FEEDBACK_BATCH` 를 받지 않아 긴 평가가 추천을 막지 않는다.
   (작업기 안 매시간 스케줄 — 20개 또는 24시간 — 은 로컬·시험용 `REPLY_AI_FEEDBACK_BATCH_ENABLED` 로만 쓴다.)

## 작업(`ai_job`)

| job_type | job_key | payload | 넣는 쪽 | 결과 |
| --- | --- | --- | --- | --- |
| ANALYSIS | `analysis:{analysis_input_id}` | `{"analysis_input_id": N}` | app | `analysis_snapshot` |
| GENERIC | `generic:{analysis_id}:{version_id}:{gallery_snapshot_id}:r{revision_no}` | `{"analysis_id", "version_id", "gallery_snapshot_id", "revision_no"}` | 작업기(분석 직후), app(누락·만료 보충) | `generation_attempt`(attempt_key=job_key) + `recommendation_set` |
| PERSONAL | `personal:{personal_input_id}` | `{"personal_input_id": N}` | app | `generation_attempt`(attempt_key=job_key), 채택 시 `recommendation_set(kind=PERSONAL)` |
| FEEDBACK_BATCH | `feedback:{batch_run_id}` | `{"batch_run_id": N}` | 작업기 스케줄러 | `batch_run` 등 |
| PURGE_VIDEO | `purge-video:{video_id}:{reason}` | `{"video_id": N, "reason"}` | app | 참고 사례·벡터·파생 결과 차단·삭제 |

- 넣을 때는 `INSERT … ON CONFLICT (job_key) DO NOTHING` 이다. 같은 키는 한 번만 생긴다.
- 상태: `QUEUED → RUNNING → SUCCEEDED`, 일시 실패는 `RETRY_WAIT`, 최종 실패는 `FAILED`(= DLQ, `failure_code` 남김), 무효화는 `CANCELLED`.
- 작업기 lease 180초, 30초마다 연장, 동시 실행 2개, 모델 호출 제한 90초. 완료 기록은 lease 소유자일 때만 한다.
- 재시도는 네트워크·429·5xx·시간 초과만 `max_attempts`(3)까지, `Retry-After` 를 따른다.
  거절(refusal)·잘린 응답(incomplete)·스키마/의미 위반은 즉시 `FAILED` 다. 검수되지 않은 후보는 어떤 경우에도 세트가 되지 않는다.
- 한 작업 안에서 이미 받은 유효 단계 결과는 `ai_job.result.stages` 에 저장해 재개 때 다시 호출하지 않는다(품질 재생성 금지).

`failure_code` 값: `TRANSPORT_EXHAUSTED`, `REFUSAL`, `INCOMPLETE`, `SCHEMA_INVALID`, `SEMANTIC_INVALID`, `BUDGET_EXCEEDED`,
`MEDIA_SOURCE_NOT_ALLOWED`, `MEDIA_DOWNLOAD_FAILED`, `MEDIA_DECODE_FAILED`, `MEDIA_BLACK_FRAMES`, `INPUT_MISSING`, `INPUT_REVOKED`.
app 은 `BUDGET_EXCEEDED` 를 `reason_code=BUDGET_EXCEEDED`, 나머지를 `ANALYSIS_FAILED`/`GENERATION_FAILED` 로 보여 준다.

## 표 소유

| 표 | 쓰는 쪽 | 비고 |
| --- | --- | --- |
| `analysis_request`, `analysis_input`, `personal_input` | app | 스냅샷은 30일 뒤 원문을 지우고 `purged_at` 을 남긴다 |
| `evidence_source` | app(행), 작업기(`cited` 표시) | 원문 대신 `text_sha` |
| `ai_job` | 둘 다 | 위 표 참고 |
| `analysis_snapshot`, `generation_attempt`, `recommendation_set` | 작업기 | app 은 무효화할 때 `status/revoked_at` 만 바꾼다 |
| `recommendation_request`, `recommendation_event`, `reply_episode`, `reply_link`, `episode_reply_snapshot` | app | |
| `version_registry`, `active_version`, `budget_day` | 작업기 | app 은 읽기만 |
| `reference_video`, `reference_pair`, `gallery_snapshot`, `embedding`, `protected_registry` | 작업기 | |
| `batch_run`, `feedback_judgment`, `feedback_case`, `validation_run`, `feedback_closure`, `version_activation_log` | 작업기 | |

원문 입력·후보·최종 카드·판정 행의 내용은 바꾸지 않는다. 상태·lease·활성 포인터·무효화 표시만 갱신한다.

## 키

- **분석 키**(`analysis_snapshot.analysis_key`) = sha256(정렬 JSON `{video_id, media_revision, caption_revision, history_snapshot_hash, access_scope_hash, preprocess_version}`).
- **분석 요청 키** = `(video_id, scope_hash, caption_revision, media_revision, preprocess_version)` 의 유효 행 하나(`uq_analysis_request_active`).
  무효가 되면 `CANCELLED` 로 두고 `revision_no + 1` 로 새로 만든다.
- **추천 키** = `analysis_id + viewer_id + personal_snapshot_hash + gallery_snapshot_id + active_version_id`.
  `recommendation_request` 는 분석 요청·버전·개인 해시로 이를 표현하며 유효 행이 하나다(`uq_recommendation_request_active`).
- `caption_revision` = sha256(캡션), `media_revision` = sha256(저장된 영상 주소),
  `scope_hash` = sha256(시청자와 차단 관계인 방 멤버 id 정렬 목록, JSON 배열). 차단이 없으면 모든 시청자가 같은 범위를 쓴다.
- `preprocess_version`·`gallery_snapshot_id` 는 활성 버전(`version_registry`)에서 읽는다.

## 기록 스냅샷(app)

- 기준 시각 T = 영상 최초 게시 시각. 기록은 현재 방의 **단체 채팅**과 **이전 영상**만 넣는다. 1:1 대화·다른 방 기록은 넣지 않는다.
- 채팅: T 이전 메시지(사용자 글·운영 글·영상 답장). 운영자가 지운 글, 방에서 빠진 멤버의 글, 시청 범위 밖(차단) 사람의 글, 글 없는 사진은 뺀다.
  영상 답장은 `parent_source_ids=["video:<id>"]`, 채팅 답글은 `["chat:<id>"]`.
- 이전 영상: T 이전에 올린 같은 방 영상(현재 영상·삭제·숨김·범위 밖 제외). 텍스트는 T 시점 캡션과,
  있으면 그 영상의 분석 결과 `current_visual_description`. T 시점 캡션은 `video_caption_revisions`(수정 전 값)로 복원한다.
  이 이력은 #558 배포 뒤의 수정부터 남으므로, 그 전에 바뀐 캡션은 현재 값과 구분할 수 없다(배포 뒤 시작한 방은 정확하다).
- 기록은 자르지 않고 방의 T 이전 단체 채팅 전부를 읽는다(방은 3일이라 양이 제한된다). 토큰 예산 초과는 작업기가 판단한다.
- `source_id` 는 `chat:<message id>`·`video:<video id>`, `actor_id` 는 사용자 id 문자열 또는 `system`.
- 기록이 없으면 `history_status=NO_RECORDS`. 토큰 예산(24,000) 초과 판단은 작업기가 하고 `OVER_BUDGET` 으로 남긴다(기록을 몰래 자르지 않는다).
- 개인화 기록: 같은 허용 기록 안에서 시청자 본인 기록(`own`)과, 부모·자식 관계로 2단계까지 이어지는 업로더 기록(`interaction`)·
  그 밖의 부모 기록(`parent_context`). 타인 기록은 문맥 해석에만 쓰고 시청자 경험의 근거로 쓰지 않는다.

## 무효화

app 은 추천 조회 때마다 다음을 확인하고, 하나라도 해당하면 저장된 추천을 내보내지 않는다.

| 상황 | 처리 |
| --- | --- |
| 영상 삭제·숨김, 업로더 탈퇴·퇴장, 시청자↔업로더 차단 | `UNAVAILABLE`(카드 없음) |
| 캡션 수정, 시청자의 차단 범위 변화, 활성 버전 변경 | 키가 달라져 새 요청을 만든다 |
| 분석 입력의 출처(채팅·이전 영상)가 삭제·숨김 | 분석·세트를 `REVOKED`, 분석 요청을 `CANCELLED` 로 두고 새로 만든다 |
| 세트 생성 24시간 경과 | 새 revision 으로 다시 만든다 |

영상이 삭제·숨김되면 app 이 `PURGE_VIDEO` 작업을 넣어 작업기가 참고 사례·벡터 등 파생 자료를 지운다.

## 보관과 삭제

- **보관 기간 30일**(app 이 매일 04:35): 방 기록·개인 기록·캡션·원본 주소(입력 스냅샷), 분석 설명·인용·장면 정보,
  후보·검수·검색 기록, 카드 문구, 답장 원문 스냅샷을 지운다. 해시·id·상태·삭제 사실(`purged_at`, `RETENTION`)만 남는다.
- **영상 삭제·숨김**: 조회 즉시 막고, 10분마다 보관 기간 안의 분석 영상을 확인해 추천을 무효로 하고 `PURGE_VIDEO` 를 넣는다.
- **회원 탈퇴**: 조회 즉시 막는다(`OWNER_WITHDRAWN`). 탈퇴 90일 파기(#555)가 그 사람의 시청 기록(사건·episode·연결·요청·개인화 입력)과
  본인 영상에서 나온 분석·추천을 지운다. 다른 사람 영상 분석에 인용된 본인 채팅은 채팅 원문처럼 남고, 그 분석의 원문·파생 자료는 30일 정리가 지운다.

## API (app)

공통 접두부 `/api/v1/rooms/{roomId}/video-posts/{videoId}`. 성공 응답은 `{isSuccess, data}`, JSON 은 snake_case.

| 메서드·경로 | 동작 |
| --- | --- |
| `POST /reply-recommendations` | 본문 없음. 유효한 결과를 돌려주거나 작업을 만든다. `PENDING` 이면 202, 나머지 200 |
| `GET /reply-recommendations` | 조회만. 요청이 없으면 200 `UNAVAILABLE`·`request_id="none"`·`reason_code=NOT_REQUESTED` |
| `POST /reply-recommendation-events` | 노출·선택·입력 시작·닫기. 204, 같은 `event_id` 다른 내용 409, 형식 오류 422 |
| `POST /reply` (기존) | 선택 항목 `recommendation_source {set_id, card_id|null, session_id}` 추가. 잘못된 출처는 연결만 빼고 답장은 저장 |

`data` = `{status, request_id, set_id, version_id, cards:[{card_id, category, title}], retry_after_ms, reason_code}`.
`title` 은 「볼까요?」로 끝나는 30자 이하 문구다(앱 추천 칸 두 줄, 작업기 생성 규칙).

| status | 규칙 |
| --- | --- |
| `PENDING` | `cards=[]`, `set_id=null`, `retry_after_ms>0` |
| `READY` | 카드 1~3개, `set_id`·`version_id` 있음 |
| `EMPTY` | `cards=[]`, `set_id`·`version_id` 있음 |
| `UNAVAILABLE` | `cards=[]`, `set_id=null`, `reason_code` 있음 |

`reason_code`: `NOT_REQUESTED`, `FEATURE_DISABLED`, `NOT_CONFIGURED`, `OWN_VIDEO`, `VIDEO_UNAVAILABLE`, `BLOCKED`, `OWNER_WITHDRAWN`, `OWNER_LEFT`,
`ROOM_CLOSED`, `BUDGET_EXCEEDED`, `ANALYSIS_FAILED`, `GENERATION_FAILED`, `TIMED_OUT`(10분 넘게 끝나지 않음), `INVALIDATED`.
방 멤버가 아니면 403, 다른 방 영상이면 404 다(기존 답장 API 와 같다).
새 AI 작업을 만드는 POST 는 시청자당 분당 10회로 막고(429), 이미 준비된 결과를 돌려주는 요청은 세지 않는다.

## 설정

app(`haruharu.reply-recommendation.*`, 기본 모두 OFF)

| 키 | 환경 변수 | 기본 |
| --- | --- | --- |
| `enabled` | `REPLY_RECOMMENDATION_ENABLED` | false |
| `personalization-enabled` | `REPLY_RECOMMENDATION_PERSONALIZATION_ENABLED` | false |
| `ensure-rate-limit-per-minute` | `REPLY_RECOMMENDATION_ENSURE_RATE_LIMIT` | 10 |
| `collection.real-user-enabled` | `REPLY_RECOMMENDATION_REAL_USER_COLLECTION_ENABLED` | false |
| `collection.qa-user-ids` | `REPLY_RECOMMENDATION_QA_USER_IDS` | 빈 값 |
| `room-ids` | `REPLY_RECOMMENDATION_ROOM_IDS` | 빈 값(모든 방) |

수집 스위치가 꺼져 있으면 실제 사용자의 사건·답장 연결·답장 원문은 저장하지 않는다. `qa-user-ids` 계정만 `provenance=QA` 로 저장한다.

`room-ids` 를 채우면 그 방에서만 켜진다(게시 분석 요청·추천 요청·사건·답장 연결 모두). 다른 방은 `UNAVAILABLE(FEATURE_DISABLED)` 로
숨긴다. 운영은 개인정보처리방침(OpenAI·Cloudflare 국외 이전·위탁, 추천 개선 이용) 개정·공지 전까지 운영진 시험 방만 넣고,
개정 뒤 비워 전체에 연다(2026-11-09 결정).

작업기 설정은 `ai-worker/README.md` 에 있다(모델·임베딩 버전·비용 한도·배치 스위치).

## 운영 배포

AWS 자원은 `infra/ai-worker/stack.yml`(CloudFormation, 환경마다 한 스택)이 만든다: ECR `haruharu-ai-worker`(계정에 하나),
작업기 서비스 `haruharu-ai-worker-svc-{env}`(Fargate arm64 1 vCPU·2 GiB, 앱과 같은 서브넷·보안 그룹), 월 1회 작업 정의
`haruharu-ai-worker-batch[-dev]` 와 예약 `haruharu-ai-feedback-monthly-{env}`, 로그 `/ecs/haruharu-ai-worker-{env}`(30일),
비밀값 `haruharu/{env}/ai-worker`(OPENAI_API_KEY·CLOUDFLARE_API_TOKEN·REPLY_AI_DATABASE_URL — 사람이 콘솔에서 넣는다),
GitHub 배포 역할에 이 작업기만 배포하는 권한. develop 에 병합하면 `image-build` 가 같은 SHA 로 작업기 이미지도 굽고,
`deploy-ecs`(all)가 작업기 스택이 있는 환경에서 서비스·월 1회 작업 정의를 같은 SHA 로 올린다(없는 환경은 건너뜀).

1. **앱 배포 전에** `558-reply-recommendations-up.sql` 적용(dev·운영 각각). 운영은 시작할 때 스키마를 검증(`ddl-auto: validate`)하고,
   앱은 기능 스위치와 무관하게 캡션 수정 때 `video_caption_revisions` 를 쓴다. 표가 없으면 앱이 뜨지 않는다.
2. 작업기 전용 DB 계정(`haruharu_ai_worker`, `ai_reply` 만 읽고 쓰기, 연결 한도) — up SQL 끝의 주석.
3. 작업기 스택 만들기(DesiredCount 0, 예약 꺼짐) → 비밀값 세 키 넣기 → PR 병합으로 이미지 빌드.
4. 작업기 `init-version` 으로 첫 버전·빈 참고 사례 묶음 등록.
5. `deploy-ecs`(dev → prod, 이미지 SHA) 뒤 스택 업데이트로 작업기 1개·예약 켜기(ImageTag 는 배포한 SHA).
6. app 작업 정의 환경 변수: `REPLY_RECOMMENDATION_ENABLED=true`, `REPLY_RECOMMENDATION_ROOM_IDS=<시험 방>`,
   `REPLY_RECOMMENDATION_QA_USER_IDS=<운영진 계정>`. 처리방침 개정·공지 뒤 `ROOM_IDS` 를 비우고, 실사용자 수집
   (`REPLY_RECOMMENDATION_REAL_USER_COLLECTION_ENABLED`)과 스택 `RealUserFeedback` 을 켠다. 개인화는 별도 승인 후.

스택을 다시 고칠 때는 `ImageTag` 를 지금 배포된 SHA 로 넘긴다(아니면 CI 가 올린 리비전 대신 스택의 이미지로 돌아간다).
끄기: app `REPLY_RECOMMENDATION_ENABLED=false`(즉시 숨김) → 작업기 DesiredCount 0·예약 끄기.

정리 잡(보관 기간 삭제·삭제 영상·지워진 답장)은 스위치를 꺼도 계속 돈다. 되돌리기(`down.sql`)는 `ai_reply` 만 지우고
`video_caption_revisions` 는 남긴다 — #558 앱 코드를 되돌려 배포한 뒤에만 따로 지운다.

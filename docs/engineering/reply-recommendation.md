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

## Depth 평가 모델 (#559)

생성 AI 는 공통 맥락 C + 개인 기록 P 로 후보를 만들고, Depth 평가 모델(`microsoft/DialogRPT-depth`, 고정 revision)은
(C, 후보 하나) → 점수 하나를 낸다. 최종 카드를 점수순으로 보여 주고, 사용자는 고르거나 고쳐서 직접 보낸다.
점수는 같은 C 의 후보끼리 비교하는 값이다(0.8 을 확률 80% 로 쓰지 않는다).

- **C**: 영상 게시 시점에 고정된 분석 스냅샷의 공개 근거(분석이 인용한 게시 전 단체 대화 + 캡션·장면 설명). 평가·학습 모두 같은 C 를 쓰고,
  P 는 생성에만 쓴다. 모델이 영어 Reddit 으로만 학습돼 C·후보를 영어로 바꿔 넣고 한국어 원문은 함께 남긴다.
- **카드 정렬**(작업기): 검수·선택이 끝난 최종 카드를 depth-serve 점수 내림차순으로 저장한다. 서비스·변환이 실패하면 원래 순서(fail-open).
  기록은 `recommendation_set.internal_cards[].depth`·`generation_attempt.candidates.depth_ranking`.
- **라벨**(app): 실제 전송한 영상 답장(`reply_link`)마다 관찰 기간(답장 시각부터 `observation-hours`, 모든 답장에 같은 기간)이 끝난 뒤
  `chat_messages.reply_to_message_id` 를 따라 Depth(답장 r → 상대 → 나 → 상대 = 3, 답글 없으면 0)·Width(직접 답글 수)를 한 번 계산해
  `ai_reply.reply_depth_observation` 에 넣는다. 연결이 끊기거나 불명확하면 `UNOBSERVED`(depth·width null, 0 과 다르다).
  전송되지 않은 후보에는 라벨이 없다. 미래 대화는 라벨로만 쓰고 모델 입력에 넣지 않는다. 회원 탈퇴 파기 때 함께 지운다.
- **예측·실측 연결**(작업기 명령 `reply-ai depth export-outcomes`): 실제 답장 1건 = 1행. 추천 당시 노출 카드·예측 점수·model_id·선택 카드와
  실제 전송 문장(수집 허용 출처만)·그 문장의 점수(지정한 모델로 다시 매김)·Depth·Width·관측 상태, 연결 구분(카드 그대로 / 카드 보고 수정 /
  직접 작성 / 추천 노출 없음, 문자 유사도 임계값은 인자)을 잇고, Spearman·쌍 비교 정확도·연결 구분별 평균 Depth·미관측 수를 요약한다.
- **추가 학습·교체**(`build-pairs` → `train` → `eval`): 같은 영상의 실제 답장끼리 depth 큰 쪽을 r+ 로 (C, r+, r−)(동점 제외),
  손실 −log σ(h(C,r+) − h(C,r−)) 로 공개 가중치에서 이어서 학습, 학습에 쓰지 않은 방(미사용 기수)의 쌍 비교 정확도가 공개 모델보다
  높을 때만 교체 가능으로 판정한다. 교체는 사람이 depth-serve 가 읽는 모델을 바꾸는 것이다(자동 아님).

| 표 | 정의 | 쓰는 쪽 | 읽는 쪽 |
| --- | --- | --- | --- |
| `ai_reply.reply_depth_observation` | `scripts/sql/559-reply-depth-up.sql`(되돌리기 `-down`, 확인 `-verify`) | app 스케줄러(10분마다) | 작업기 `depth export-outcomes` |

스위치(모두 기본 OFF, 끄면 기존 동작과 같다)

| 쪽 | 키 / 환경 변수 | 기본 |
| --- | --- | --- |
| app 라벨 수집 | `haruharu.reply-recommendation.depth.enabled` / `REPLY_RECOMMENDATION_DEPTH_ENABLED` | false |
| app 관찰 기간 | `haruharu.reply-recommendation.depth.observation-hours` / `REPLY_RECOMMENDATION_DEPTH_OBSERVATION_HOURS` | 24 |
| 작업기 카드 정렬 | `REPLY_AI_DEPTH_RANKING_ENABLED`(서비스 주소 `REPLY_AI_DEPTH_URL`) | false |

라벨 수집을 켜기 전에 559 SQL 을 적용한다. 명령·서비스 실행법은 `ai-worker/README.md` 의 Depth 절에 있다.

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

AWS 자원은 `infra/ai-worker/stack.yml`(CloudFormation, 환경마다 한 스택)이 만든다: ECR `haruharu-ai-worker`·`haruharu-ai-worker-depth`
(각각 계정에 하나), 작업기 서비스 `haruharu-ai-worker-svc-{env}`(Fargate arm64, 앱과 같은 서브넷·보안 그룹), 월 1회 작업 정의
`haruharu-ai-worker-batch[-dev]` 와 예약 `haruharu-ai-feedback-monthly-{env}`, 로그 `/ecs/haruharu-ai-worker-{env}`(30일),
비밀값 `haruharu/{env}/ai-worker`(OPENAI_API_KEY·CLOUDFLARE_API_TOKEN·REPLY_AI_DATABASE_URL — 사람이 콘솔에서 넣는다),
GitHub 배포 역할에 이 작업기만 배포하는 권한. develop 에 병합하면 `image-build` 가 같은 SHA 로 작업기 이미지도 굽고,
`deploy-ecs`(all)가 작업기 스택이 있는 환경에서 서비스·월 1회 작업 정의를 같은 SHA 로 올린다(없는 환경은 건너뜀).

**Depth 사이드카**(스택 `DepthRanking=true`, 기본): 추천 작업 태스크에 컨테이너 `depth`(`ai-worker/Dockerfile.depth` —
CPU torch + `microsoft/DialogRPT-depth` 가중치 revision 고정, 인터넷 없이 시작)가 같이 뜨고, 작업기는
`REPLY_AI_DEPTH_URL=http://127.0.0.1:18453` 로 점수를 받는다. depth 는 시작 때 공식 점수 5개를 재현해야 포트를 열고(`/health` 200),
작업기 컨테이너는 depth 가 HEALTHY 가 된 뒤 시작한다. 태스크는 2 vCPU·6 GB(depth 상시 약 1.8 GB, 후보 16개 계산 때 최대 약 2.1 GB),
`DepthRanking=false` 면 사이드카 없이 1 vCPU·2 GB·정렬 끔이다. 개선 루프(batch) 작업에는 넣지 않는다.
depth 이미지(압축 약 1.6 GB)는 `image-build` 가 그 이미지에 들어가는 파일(`Dockerfile.depth`·`pyproject.toml`·`uv.lock`·`README.md`·`reply_ai/`)이
바뀐 병합에서만 굽는다. `deploy-ecs` 는 `depth_image_tag` 를 줄 때만 depth 이미지를 바꾸고, 비우면 지금 이미지를 유지한다.

처음 켜는 순서(prod 기준, dev 도 같다):

1. **앱 배포 전에** `558-reply-recommendations-up.sql` → `559-reply-depth-up.sql` 적용(각 `-verify.sql` 로 확인). 운영은 시작할 때
   스키마를 검증(`ddl-auto: validate`)하고, 앱은 기능 스위치와 무관하게 캡션 수정 때 `video_caption_revisions` 를 쓴다.
   표가 없으면 앱이 뜨지 않는다.
2. 작업기 전용 DB 계정(`haruharu_ai_worker`, `ai_reply` 만 읽고 쓰기, 연결 한도) — 558 up SQL 끝의 주석. 559 표에는 같은 계정에
   `SELECT` 를 준다(559 up SQL 끝의 주석).
3. 스택 만들기(`DesiredCount=0`, `ScheduleState=DISABLED`). 파라미터 값 출처:
   - `Subnets`·`SecurityGroups`·`AssignPublicIp`: 앱 서비스와 같은 값 —
     `aws ecs describe-services --cluster haruharu --services haruharu-app-svc-prod --query 'services[0].networkConfiguration.awsvpcConfiguration'`.
   - `MediaBucket`: 앱 작업 정의의 `AWS_S3_BUCKET` 값.
   - `CloudflareAccountId`: Cloudflare 대시보드 계정 ID(Workers AI 를 쓰는 계정).
   - `ImageTag`·`DepthImageTag`: 4번에서 올릴 이미지 태그(커밋 SHA).
   - `CreateRepository`·`CreateDepthRepository`: 그 ECR 저장소를 처음 만드는 스택만 `true`(이미 다른 환경 스택이 만들었으면 `false`).
   - `DepthRanking`: `true`(기본). 나머지는 기본값.
   ```sh
   aws cloudformation create-stack --stack-name haruharu-ai-worker-prod --template-body file://infra/ai-worker/stack.yml \
     --capabilities CAPABILITY_NAMED_IAM --parameters ParameterKey=Env,ParameterValue=prod \
     ParameterKey=Subnets,ParameterValue='subnet-a\,subnet-b' ParameterKey=SecurityGroups,ParameterValue=sg-x \
     ParameterKey=AssignPublicIp,ParameterValue=ENABLED ParameterKey=MediaBucket,ParameterValue=<버킷> \
     ParameterKey=CloudflareAccountId,ParameterValue=<계정 ID> ParameterKey=ImageTag,ParameterValue=<SHA> \
     ParameterKey=DepthImageTag,ParameterValue=<SHA> ParameterKey=CreateRepository,ParameterValue=<true|false> \
     ParameterKey=CreateDepthRepository,ParameterValue=<true|false>
   ```
4. 비밀값 `haruharu/prod/ai-worker` 의 세 키를 콘솔에서 채운다(템플릿의 SecretString 은 빈 칸이고 다시 덮어쓰지 않는다).
5. 이미지 두 개를 같은 태그로 올린다. 병합 전이면 직접 굽는다(병합 뒤에는 `image-build` 가 굽는다):
   ```sh
   aws ecr get-login-password | docker login --username AWS --password-stdin <계정>.dkr.ecr.ap-northeast-2.amazonaws.com
   docker buildx build --platform linux/arm64 -t <ECR>/haruharu-ai-worker:<SHA> --push ai-worker
   docker buildx build --platform linux/arm64 -f ai-worker/Dockerfile.depth -t <ECR>/haruharu-ai-worker-depth:<SHA> --push ai-worker
   ```
6. 작업기 `init-version` 으로 첫 버전·빈 참고 사례 묶음 등록.
7. 스택 업데이트로 `DesiredCount=1`(필요하면 `ScheduleState=ENABLED`). 나머지 파라미터는 `UsePreviousValue=true`.
   로그 `/ecs/haruharu-ai-worker-prod` 의 `depth/` 스트림에 `depth_ready`(공식 점수 5개)가 찍히고 서비스가 안정되는지 확인한다.
8. app 작업 정의 환경 변수: `REPLY_RECOMMENDATION_ENABLED=true`, `REPLY_RECOMMENDATION_ROOM_IDS=<시험 방>`,
   `REPLY_RECOMMENDATION_QA_USER_IDS=<운영진 계정>`, Depth 라벨 수집 `REPLY_RECOMMENDATION_DEPTH_ENABLED=true`
   (관찰 기간 `REPLY_RECOMMENDATION_DEPTH_OBSERVATION_HOURS`, 기본 24). 처리방침 개정·공지 뒤 `ROOM_IDS` 를 비우고, 실사용자 수집
   (`REPLY_RECOMMENDATION_REAL_USER_COLLECTION_ENABLED`)과 스택 `RealUserFeedback` 을 켠다. 개인화는 별도 승인 후.

스택을 다시 고칠 때는 `ImageTag`·`DepthImageTag` 를 지금 배포된 태그로 넘긴다(아니면 CI 가 올린 리비전 대신 스택의 이미지로 돌아간다).
끄기: app `REPLY_RECOMMENDATION_ENABLED=false`(즉시 숨김) → 작업기 DesiredCount 0·예약 끄기. Depth 정렬만 끄려면 스택
`DepthRanking=false`(사이드카 제거, 카드는 생성 순서). depth 가 응답하지 않아도 작업기는 원래 순서로 저장한다(fail-open).

정리 잡(보관 기간 삭제·삭제 영상·지워진 답장)은 스위치를 꺼도 계속 돈다. 되돌리기(`down.sql`)는 `ai_reply` 만 지우고
`video_caption_revisions` 는 남긴다 — #558 앱 코드를 되돌려 배포한 뒤에만 따로 지운다.

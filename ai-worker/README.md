# reply-ai — 영상 답장 추천 AI 작업기 (#558)

앱 서버가 `ai_reply` 스키마에 넣은 작업을 가져가 영상 이해 → 유사 사례 검색 → 후보 생성 → 독립 검수 → 최종 카드(최대 3개)를 만들고,
실제 답장이 쌓이면 피드백 배치로 프롬프트 개선안을 검증한다. 앱 서버·대기열 계약은
[`docs/engineering/reply-recommendation.md`](../docs/engineering/reply-recommendation.md), 표 정의는
[`scripts/sql/558-reply-recommendations-up.sql`](../scripts/sql/558-reply-recommendations-up.sql)이다.

작업기는 `ai_reply` 스키마만 읽고 쓴다. 서비스 표(영상·채팅·차단)는 보지 않는다. 권한·시점이 확인된 스냅샷은 앱 서버가 만든다.

## 설치

```sh
cd ai-worker
uv sync                    # 작업기(+테스트)
uv sync --extra embedding  # 임베딩 서비스까지(torch·sentence-transformers)
```

Python 3.12, ffmpeg/ffprobe 필요(`/opt/homebrew/bin` 또는 PATH, `REPLY_AI_FFMPEG`·`REPLY_AI_FFPROBE` 로 바꿀 수 있다).

## 환경 변수

| 이름 | 기본 | 설명 |
| --- | --- | --- |
| `REPLY_AI_DATABASE_URL` | (필수) | `postgresql://…/db`. 운영은 ai_reply 전용 계정 |
| `OPENAI_API_KEY` | (필수) | 값은 출력·기록하지 않는다. 로컬은 `--dotenv`(여러 번)·`REPLY_AI_DOTENV`(쉼표)로 .env 의 이 키·`CLOUDFLARE_*` 만 읽는다 |
| `REPLY_AI_MODEL` | `gpt-6.1-terra` | Responses API, reasoning low, store=false, 도구 없음 |
| `REPLY_AI_EMBEDDING_PROVIDER` | `local_http` | `cloudflare_workers_ai`(운영) 또는 `local_http`(로컬 embedding-serve) |
| `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN` | 빈 값 | Workers AI 전용 토큰(읽기·편집). 운영은 Secrets Manager 에서 주입, 값은 출력·기록하지 않는다 |
| `REPLY_AI_EMBEDDING_TIMEOUT_SECONDS`, `REPLY_AI_EMBEDDING_CONCURRENCY` | 30, 2 | Cloudflare 호출 시간 초과·프로세스 전체 동시 호출 |
| `REPLY_AI_EMBEDDING_MAX_INPUT_BYTES` | 4096 | 질의 지시 포함 UTF-8 바이트(토큰 ≤ 바이트). 넘으면 자르지 않고 `INPUT_TOO_LONG` |
| `REPLY_AI_EMBEDDING_URL` | `http://127.0.0.1:18452` | 로컬 임베딩 서비스(`local_http`) |
| `REPLY_AI_MEDIA_ALLOWED_PREFIXES` | 빈 값 | HTTP 로 받아도 되는 영상 주소 접두사(쉼표). 그 밖의 주소는 받지 않는다 |
| `REPLY_AI_S3_BUCKET`, `REPLY_AI_S3_REGION` | 빈 값, `ap-northeast-2` | 이 버킷 주소(`https://{bucket}.s3.{region}.amazonaws.com/…`)는 boto3 로 받는다 |
| `REPLY_AI_DAILY_BUDGET_USD` | 10 | 온라인 생성 하루(KST) 한도 |
| `REPLY_AI_FEEDBACK_BUDGET_USD` | 10 | 피드백 배치 실행당 한도 |
| `REPLY_AI_PRICE_INPUT_PER_M`, `REPLY_AI_PRICE_OUTPUT_PER_M` | 1, 5 | 100만 토큰당 달러(비용 예약·기록용) |
| `REPLY_AI_FEEDBACK_BATCH_ENABLED` | false | 작업기 안 매시간 배치 스케줄(로컬·시험용). 운영은 끄고 월 1회 `batch run-scheduled` |
| `REPLY_AI_AUTOMATIC_ACTIVATION_ENABLED` | false | 검증 통과 버전 자동 적용(CAS). 끄면 VALIDATED 로 둔다 |
| `REPLY_AI_REAL_USER_FEEDBACK_ENABLED` | false | 실제 사용자 episode 를 배치에 넣을지(기본은 QA·합성만) |
| `REPLY_AI_CONCURRENCY` | 2 | 동시 작업 수 |
| `REPLY_AI_LEASE_SECONDS`, `REPLY_AI_HEARTBEAT_SECONDS` | 180, 30 | 작업 lease·연장 주기 |
| `REPLY_AI_INPUT_TOKEN_BUDGET` | 24000 | 분석 입력 예산. 넘으면 기록 없이 현재 영상만(OVER_BUDGET) |
| `REPLY_AI_EMBEDDING_REVISION` | `97b0c614be4d77ee51c0cef4e5f07c00f9eb65b3` | 고정 revision(40자 커밋만 허용) |
| `REPLY_AI_EMBEDDING_DEVICE` | `cpu` | 장치도 임베딩 버전(model_id)에 들어간다 |

## 명령

```sh
uv run reply-ai init-version [--gallery ID]           # 첫 버전(패키지 프롬프트·설정)과 활성 포인터. 멱등
uv run reply-ai gallery import fixtures/synthetic_gallery.json   # 합성 사례 → 새 gallery_snapshot(CANDIDATE)
uv run reply-ai version use-gallery --gallery ID --reason "…"    # 로컬 개발용: 사례 묶음만 바꾼 버전을 CAS 로 활성화
uv run reply-ai version show | rollback --version-id ID --reason "…"
uv run --extra embedding reply-ai embedding-serve      # 127.0.0.1:18452
uv run reply-ai worker [--once] [--types ANALYSIS,GENERIC]
uv run reply-ai batch run-scheduled [--deadline-minutes 120]   # 정기 실행(운영 월 1회): 배치를 만들어 끝까지 돌리고 끝낸다
uv run reply-ai batch tick [--force] | status
uv run reply-ai embedding probe [--write] | check      # Cloudflare 연결 시험·점검 기준(resources/embedding_probe.json)·출력 변화 점검
uv run reply-ai gallery reindex --gallery ID           # 묶음 전체를 지금 임베딩 버전으로 채우기(이어서 가능, 활성화 안 함)
uv run reply-ai probe-schema [--write]                 # strict 구조화 출력 키워드 확인(실제 호출 1~8번)
```

### 로컬(devstack DB 127.0.0.1:5465 `haruharu_devstack`)

```sh
# 0) 표: scripts/sql/558-reply-recommendations-up.sql
# 1) 임베딩 서비스(첫 실행 때 고정 revision 을 내려받는다. 이후엔 HF_HUB_OFFLINE=1)
HF_HUB_OFFLINE=1 uv run --extra embedding reply-ai embedding-serve --port 18452
# 2) 버전·합성 사례
export REPLY_AI_DATABASE_URL=postgresql://haruharu@127.0.0.1:5465/haruharu_devstack
uv run reply-ai init-version
uv run reply-ai gallery import fixtures/synthetic_gallery.json        # → gallery_snapshot_id
uv run reply-ai version use-gallery --gallery <id> --reason "local dev synthetic gallery"
# 3) 작업기(미디어 서버 주소를 허용 목록에)
REPLY_AI_EMBEDDING_URL=http://127.0.0.1:18452 REPLY_AI_MEDIA_ALLOWED_PREFIXES=http://<devstack 미디어 호스트>:8090/ \
  uv run reply-ai --dotenv /Users/lewis/haru/haruharu-BE/.env worker
```

## 명세 대응

| 명세 | 구현 |
| --- | --- |
| 3장 작업 상태·재시도·lease | `jobs.py`(SKIP LOCKED, lease 180초·heartbeat 30초, 소유자만 완료, 전송 실패만 3회·Retry-After), `worker.py` |
| 3장 첫 유효 결과 보존 | 단계 결과를 `ai_job.result.stages` 에 먼저 남기고 재개 때 다시 부르지 않음(`pipeline.JobContext.model_call`) |
| 3장 비용 한도 | `budget.py`(단계 전 최대 비용 예약 → 실제 비용 정산, 하루 KST·배치 실행당) |
| 4장 프레임·분석 | `media.py`(허용 출처만, 10·50·90%, 회전·실제 해상도·실제 시각·해시·도구 버전, 검은 화면·해독 실패), `analysis.py`(의미 검사) |
| 5장 입력 예산 | `tokens.py` + `pipeline.run_analysis`(초과 시 현재 영상만, `history_input_status=OVER_BUDGET`) |
| 6장 검색 | `retrieval.py`(query 템플릿·제외 규칙·정확 코사인·top3·답장 5개), `embedding*.py`, `gallery.py` |
| 7·8장 생성·검수·선택·개인화 | `generation.py`(후보 검사, 검수 검사, 선택·병합 — 연구 코드와 같은 규칙), `pipeline.run_generic/run_personal` |
| 11장 무효화 | `pipeline.run_purge`(PURGE_VIDEO) |
| 12·13장 피드백 배치 | `feedback/engine.py` |

후보 하나가 내용 규칙(제목 「볼까요?」·30자, 예문 120자, anchor 정확 일치, 근거 id, 개인 인용 정확 일치·주체)을 어기면 그 후보만 검수 전에 버리고
`generation_attempt.candidates.dropped` 에 남긴다. ID 중복·스키마 위반·개인화 선언 불일치, 검수 응답의 누락·순서 변경은 시도 전체 실패다.
검수되지 않은 후보는 어떤 경우에도 세트가 되지 않는다.

### 명세 패키지에 더한 것

- `08-card-quality.txt` / `card-quality.schema.json`: 13장 고정 검증의 카드 적절성 판정(실제 답장 숨김, 근거 없는 개인화 여부). 패키지에는 이 단계 지시가 없었다.
- 작업기 전용 표: `version_activation_log`(롤백 대상 확인), `feedback_closure`(닫힌 episode 를 본 순서 — 배치 watermark), `batch_run.lo/hi_closure_seq` — 모두 558 up SQL 에 있다.
- `strict_schema_support.json`: 2026-11-08 probe 결과, gpt-6.1-terra strict 모드가 maxLength·minLength·maxItems·minItems·minimum·maximum·format 을 모두 받았다(그래도 서버에서 전체 스키마로 다시 검사).

## 실패 코드(`ai_job.failure_code`)

계약 문서의 코드 + 작업기 전용 `PROVIDER_REJECTED`(API 가 요청을 거부, 4xx), `PROVIDER_FAILED`(응답이 실패 상태),
`ATTEMPTS_EXHAUSTED`(작업기 중단이 반복돼 lease 만료로 max_attempts 초과), `EMBEDDING_UNAVAILABLE`, `INTERNAL_ERROR`(버그, 재시도 안 함).

## 테스트

```sh
uv run pytest            # 단위 + PostgreSQL 통합(127.0.0.1:5465 컨테이너에 reply_ai_test_* DB 를 만들고 지운다)
REPLY_AI_DOTENV=…/.env REPLY_AI_LIVE_VIDEO=<3초 영상> uv run pytest -m live -s   # 실제 모델·임베딩(비용 약 $0.02)
```

인수 사례 대응: GEN01~03·PER01~02(`test_selection`, `test_pipeline`), RET01~04·MODEL01(`test_retrieval`, `test_pipeline`),
ASYNC02~03(`test_jobs_budget`, `test_pipeline`, `test_validators`), HIST01·03·04(`test_validators`, `test_pipeline`),
EVAL01(`test_validators`), FB01·02·04·05·06(`test_feedback`), DEL01(`test_pipeline`, 작업기 쪽). FB03·AUTH·TIME·UI 는 앱 서버·앱 범위다.

## 운영 배포(ECS Fargate)

자원은 [`infra/ai-worker/stack.yml`](../infra/ai-worker/stack.yml), 순서·스위치는
[`docs/engineering/reply-recommendation.md`](../docs/engineering/reply-recommendation.md) 「운영 배포」.

- 이미지: 이 폴더의 `Dockerfile`(python 3.12-slim + ffmpeg, arm64). develop 병합 때 `image-build` 가 `haruharu-ai-worker:<SHA>` 로 굽는다.
  torch 를 넣지 않는다(임베딩은 Cloudflare). 토큰 추정용 tiktoken 인코딩은 이미지에 미리 받아 둔다.
- 서비스: `reply-ai worker --types ANALYSIS,GENERIC,PERSONAL,PURGE_VIDEO`(개선 루프는 받지 않음).
- 개선 루프: 매달 1일 04:00 KST 에 EventBridge Scheduler 가 `reply-ai batch run-scheduled` 작업을 띄운다(자동 적용 켬, 실사용자 답장은
  처리방침 개정 전까지 끔). 새로 닫힌 관찰이 없으면 바로 끝난다.
- 운영 참고 사례 묶음은 비어 있다(실사용자 답장은 다른 방 재사용 허가 경로가 생기기 전에는 사례가 되지 않는다). 묶음이 비면
  검색 단계가 임베딩을 부르지 않는다.
- Cloudflare 는 불변 revision 을 주지 않는다. `embedding probe --write` 로 만든 점검 기준(고정 문장 4개)과 출력이 달라지면
  (코사인 0.999 미만) 임베딩을 쓰지 않는다 — 처음 쓰기 전과 그 뒤 하루마다 확인한다. 기준을 새로 만들면 revision 칸이 바뀌어
  옛 벡터와 섞이지 않고, 참고 사례는 `gallery reindex` 로 다시 채운다.

## 임베딩 모델

운영: Cloudflare Workers AI `@cf/qwen/qwen3-embedding-0.6b`(REST, 요청 방식 `text` — 질의 지시는 작업기가 문자열로 한 번 붙인다,
응답을 L2 정규화, 1024차원·개수·유한값·0 아닌 길이 검사). 벡터 기록에 제공자·모델·revision·요청 방식·역할(document)·차원·정규화를 남긴다.

로컬 개발: Qwen/Qwen3-Embedding-0.6B, revision `97b0c614be4d77ee51c0cef4e5f07c00f9eb65b3`(1,207,489,041 바이트), Apache-2.0,
<https://huggingface.co/Qwen/Qwen3-Embedding-0.6B>. 가중치 학습 없이 추론만 한다. sentence-transformers 6.2.0 · transformers 5.22.2 · torch 2.15.0,
last-token pooling, L2 정규화, float32, CPU. `model_id` 에 pooling·정밀도·라이브러리 버전·장치를 넣어(`Qwen/Qwen3-Embedding-0.6B#lasttoken#norm#fp32#st6.2.0#tf5.22.2#torch2.15.0#cpu`)
다른 체계의 벡터와 섞이지 않게 한다. 연구 때 LM Studio 로 만든 벡터는 쓰지 않는다.

## 알려진 제한

- 큰 입력 스냅샷은 명세처럼 S3 가 아니라 DB(jsonb)에 있다. 30일 뒤 원문 삭제는 앱 서버 몫(`purged_at`).
- 추천 문구(title)는 명세의 60자 대신 30자 이하다(앱 추천 칸이 두 줄까지라 폭 좁은 폰에서도 다 보이게, 2026-11-09 결정).
  지시문(02·03)·출력 형식(candidates·gate-input)·설정 `generation.title_max_chars`·코드 검사가 같은 값을 쓴다.
- 지시문·설정은 버전에 묶인다. 이미 활성 버전이 있는 환경에서 `resources` 의 지시문을 바꾸면 새 버전을 등록·활성화해야 반영된다
  (아직 CLI 명령은 없다 — 운영은 첫 배포의 `init-version` 이 새 지시문으로 등록한다).
- 이미지 토큰은 OpenAI 타일 규칙으로 추정한다(실제 사용량은 응답 usage 로 기록).
- 가격(입력 $1·출력 $5 / 100만 토큰)은 설정값이다. 바뀌면 환경 변수로 맞춘다.
- 피드백 배치의 고정 검증은 일반(비개인화) 생성만 다시 만든다. 개선안도 일반 생성·검수 지시만 바꾼다.
- 검증 세트는 앞선 배치의 사례에서 방·사람이 겹치지 않게 dev/regression 을 나눠 만든다. 한쪽이 비면 기존 버전을 유지하고 관찰은 남겨 둔다.
- 작업 전달은 운영 배포 계획의 SQS+Outbox 대신 DB 작업 표다(1초마다 확인, 2026-11-09 결정). SQS 는 나중에 깨우는 신호로만 붙일 수 있다.
- 로컬 임베딩(Qwen 고정 revision)과 Cloudflare 벡터는 섞지 않는다. 연구용 고정 입력 50개 비교(계획 5장 4)는 운영 참고 사례가
  생길 때(묶음이 비어 있는 동안은 검색이 임베딩을 쓰지 않음) 함께 한다.

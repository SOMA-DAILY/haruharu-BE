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
uv sync --extra depth      # Depth 점수 서비스까지(torch·transformers)
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
| `REPLY_AI_DEPTH_RANKING_ENABLED` | false | 최종 카드를 Depth 점수순으로 정렬. 끄면 기존 순서·기록과 같다 |
| `REPLY_AI_DEPTH_URL`, `REPLY_AI_DEPTH_TIMEOUT_SECONDS` | `http://127.0.0.1:18453`, 10 | 작업기가 부르는 depth-serve 주소·시간 초과 |
| `REPLY_AI_DEPTH_CONTEXT_CHAT_TURNS` | 4 | 공통 맥락 C 에 넣을 인용 단체 대화(최근 N 턴). 작업기·`depth export-outcomes` 가 같은 값을 쓴다 |
| `REPLY_AI_DEPTH_REVISION`, `REPLY_AI_DEPTH_MAX_SEQ_LEN`, `REPLY_AI_DEPTH_DEVICE` | 아래 고정 revision, 50, `cpu` | depth-serve 쪽 설정(모두 model_id 에 들어간다) |

## 명령

```sh
uv run reply-ai init-version [--gallery ID]           # 첫 버전(패키지 프롬프트·설정)과 활성 포인터. 멱등
uv run reply-ai gallery import fixtures/synthetic_gallery.json   # 합성 사례 → 새 gallery_snapshot(CANDIDATE)
uv run reply-ai version use-gallery --gallery ID --reason "…"    # 로컬 개발용: 사례 묶음만 바꾼 버전을 CAS 로 활성화
uv run reply-ai version show | rollback --version-id ID --reason "…"
uv run --extra embedding reply-ai embedding-serve      # 127.0.0.1:18452
uv run --extra depth reply-ai depth-serve             # 127.0.0.1:18453 (--max-seq-len 50)
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
`PROVIDER_QUOTA_EXCEEDED`(429 `insufficient_quota` — 계정 크레딧·한도 소진, 재시도 안 함. 앱에는 일시 실패로 `ANALYSIS_FAILED`/`GENERATION_FAILED`),
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
- Depth 사이드카: `Dockerfile.depth`(python 3.12-slim + CPU torch + `microsoft/DialogRPT-depth` 가중치, revision 고정, arm64).
  잠금 파일의 torch 는 linux 에서 CUDA 패키지를 끌고 오므로 그것들을 빼고 같은 버전의 `+cpu` torch 를 받는다. 이미지 안에서
  `HF_HUB_OFFLINE=1` 로 인터넷 없이 시작하고, 공식 점수 5개를 재현한 뒤 `127.0.0.1:18453` 을 연다. 추천 작업 태스크에 컨테이너 `depth`
  로 같이 뜨고(스택 `DepthRanking`, 기본 true), 작업기는 depth 가 HEALTHY 가 된 뒤 시작한다. 이미지 `haruharu-ai-worker-depth:<SHA>` 는
  이 이미지에 들어가는 파일이 바뀐 병합에서만 굽고, 배포는 `deploy-ecs` 의 `depth_image_tag` 를 줄 때만 바꾼다.
  로컬 확인: `docker build --platform linux/arm64 -f Dockerfile.depth -t depth .` → `docker run --network none depth` 뒤
  `docker exec <컨테이너> python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:18453/health').read())"`.
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

## Depth 평가 모델(카드 선택·순서)

생성 AI 는 공통 맥락 C + 개인 기록 P 로 후보를 만들고, Depth 모델은 (C, 후보 하나) → 점수 하나를 낸다.

- 검수(통과/탈락만): 8개 검사·issue none·quality ≥ 3(개인 후보는 useful_personalization 도). 품질 점수는 통과 기준으로만 쓰고
  선택 순서에는 쓰지 않는다.
- 한 풀 선택(`depth.select_pool`, `selection_policy=depth-pool-v1`): 통과 후보를 Depth 점수 내림차순(동점은 후보 번호 순)으로
  유형당 1개·의미 그룹(`redundancy_group`)당 1개·최대 3개. 고른 카드는 점수순으로 저장한다.
- 일반 세트: 일반 통과 후보 전부를 채점(영어 변환 1번에 전부)하고 한 풀 선택.
- 개인 세트: 일반 시도의 통과 후보 전체(`candidates.kept` + 검수 결과, 점수는 `depth_ranking.scores` 재사용) + 개인 통과 후보를 한 풀로
  고른다. 개인 후보만 같은 공통 C(일반 세트의 영어 C 재사용)로 채점한다. 일반 점수와 모델·영어 C 가 다르면 섞지 않는다(`SCORE_MISMATCH`).
  개인 검수 입력(`gate-input.generic_candidates`)에 일반 통과 후보와 그 의미 그룹을 넣고, 같은 의미면 그 그룹 이름을 그대로 쓰게 한다
  (지시문 `04-gate.txt` 는 새 버전을 만들 때 반영된다). 개인 후보가 하나도 뽑히지 않으면 일반 세트를 그대로 쓴다. 일반 세트에 있던 후보는
  같은 카드 id 를 쓴다.

`REPLY_AI_DEPTH_RANKING_ENABLED=false`(기본)이면 선택·순서·검수 입력·기록이 기존과 같다(일반: 품질순 `select_generic`, 개인:
`merge_personal`). 켜져 있어도 채점에 실패하면 같은 기존 선택을 쓰고 상태만 남긴다(개인 검수가 일반 그룹 이름을 썼으면 병합 규칙의
`base:<card_id>` 로 바꿔 판정한다). 13장 고정 검증 재생성은 Depth 점수가 없어 기존 품질순 선택이다.

- 모델: `microsoft/DialogRPT-depth`, revision `0fa8a8770e267801a5779788ccfd921192ae7f40`(MIT, GPT-2 medium 24층,
  `GPT2ForSequenceClassification` num_labels 1). 영어 Reddit 으로만 학습돼 입력은 영어로 바꿔 넣는다. 가중치 학습 없이 추론만 한다.
- 서비스: `reply-ai depth-serve`(`depth_service.py`, extra `depth` 전용 — 작업기 이미지에는 넣지 않는다). `POST /score`
  `{context_en: [턴…], candidates_en: [예문…]}` → `{scores, model_id, model_revision, max_seq_len, context_token_cap, inputs}`,
  `GET /info`·`/health`. `model_id` 는 모델·revision 앞 12자·max_seq_len·C 토큰 상한·정밀도·라이브러리 버전·장치
  (`microsoft/DialogRPT-depth@0fa8a8770e26#len50#ctx25#fp32#tf5.22.2#torch2.15.0#cpu`).
- 입력: 맥락 턴마다 `<|endoftext|>`(50256) 로 끝내고 답장을 붙인다. C(구분자 포함)는 `REPLY_AI_DEPTH_CONTEXT_TOKEN_CAP`(기본 25, 0 이면
  상한 없음) 토큰으로 앞쪽부터 자르고 답장은 자르지 않는다. 합이 50 토큰을 넘으면 C 를 앞쪽부터 더 자른다. 답장 혼자 50 토큰을 넘을 때만
  답장 뒤쪽을 자르고 C 없이 넣는다(`reply_truncated`). depth-serve·`build-pairs`·`train`·`eval` 이 같은 값을 써야 한다(`input_profile`).
  점수 = 답장 마지막 토큰(길이−1)의 logit → sigmoid. 구분자와 패딩이 같은 50256 이라 분류 헤드의 자동 위치 선택은 쓰지 않고
  `model.transformer` 은닉상태 + `model.score` 로 직접 계산한다.
- 시작 점검: 공식 코드 출력([`doc/toy.tsv.ensemble.jsonl`](https://github.com/golsun/DialogRPT/blob/master/doc/toy.tsv.ensemble.jsonl)
  의 depth 5개)을 1e-3 안에서 재현하지 못하면 시작하지 않는다(실제로는 소수 여섯째 자리까지 같다). 모델 카드 표의
  "Me too!" 0.032 는 같고, "Can anyone recommend a nice review paper?" 0.724 는 공식 계산 방식으로 나오지 않는다(0.5986, 순서는 같음).
  공식 예시는 짧아 C 25 토큰 상한에 걸리지 않는다.
- 공통 맥락 C: 분석 스냅샷(영상 게시 시점 고정)의 공개 근거만 — 분석이 인용한 게시 전 단체 대화 중 최근 N 턴(입력 순서) + 영상 게시글
  (캡션 + 장면 설명). 개인 기록 P 는 넣지 않는다. 개인 세트도 일반 세트의 영어 C 를 그대로 다시 쓰고 새 카드 예문만 변환한다.
- 영어 변환: 기존 provider 로 strict 스키마 1번(`09-depth-translate.txt` / `depth-translate.schema.json`, 단계 이름 `depth_translate:<입력 sha>`).
  입력이 50 토큰이라 C 를 그대로 옮기면 장면 설명 꼬리만 남는다. 그래서 변환 단계가 C 를 영어 한두 문장(영상 내용·캡션·단체 대화 요지,
  25 토큰 이하, 240자 이하)으로 압축하고, 점수 입력의 맥락은 이 한 턴이다. 예문은 압축하지 않고 그대로 옮긴다.
  비용 예약·단계 캐시는 다른 모델 단계와 같다. 지시문은 버전 레지스트리에 묶지 않고 sha 를 기록에 남긴다.
- 기록: 한국어 원문은 바꾸지 않는다. 카드마다 `internal_cards[].depth = {score, rank, example_en, model_id}`,
  시도마다 `generation_attempt.candidates.depth_ranking`(상태·선택 정책·영어 C·후보 영어·점수·순서·model_id·변환 지시문 sha·
  C 에 넣은/인용된 대화 턴 수·후보별 실제 입력 토큰 수와 잘림 여부 `inputs[] = {context_tokens, context_used_tokens, reply_tokens,
  input_tokens, used_tokens, truncated, context_truncated, reply_truncated}`). 공개 `cards` 는 점수순이다.
- 일반 세트 기록(`depth_ranking`, `scope=candidates`): 후보 전체 점수(`scores[]`, candidate_id 기준), 풀 순서(`candidate_order`),
  점수순(`prioritized_order`), Depth 없이 골랐을 카드(`baseline_selected`)와 고른 카드(`selected`), `selection_changed`·
  `added_by_depth`·`removed_by_depth`, 표시 순서(`ranked_order`).
- 개인 세트 기록(`scope=pool`): 풀 순서(`pool_order`)·일반/개인 후보 id·다시 쓴 일반 점수(`generic_scores_reused`, 출처 `generic_scores_from`)·
  풀 전체 점수·고른 카드(`selected`)·그중 개인 후보(`personal_selected`)·기존 병합이었으면 고를 카드(`baseline_selected`).
  개인화 생성 입력(base_cards)과 기존 병합은 표시 순서가 아니라 기존 선택 순서(품질·후보 번호)로 한다.
- 재시도: 점수 단계(`depth_score`)는 후보 id 목록과 함께 저장하고, 재시도에서 후보가 새로 만들어져 id 가 다르면 버리고 다시 계산한다
  (예문이 같으면 변환은 캐시를 쓴다). 서비스는 맥락 턴이 32개를 넘어도 거절하지 않고 최근 32턴만 쓴다.
- 실패: 서비스 꺼짐·오류(`SERVICE_UNAVAILABLE`), 응답 형식 위반(`SERVICE_INVALID`), 영어 변환 실패·비용 한도(`TRANSLATION_FAILED`),
  캐시·응답을 카드에 대응시키지 못함(`RANKING_INVALID`), 개인 풀의 점수 불일치(`SCORE_MISMATCH`)는
  기존 품질순 선택을 쓰고 상태만 남긴다(fail-open, 생성·검수는 다시 부르지 않는다). 서비스를 먼저 확인하고 꺼져 있으면 변환을 부르지 않는다.
  일반 통과 후보가 2개 미만이면 `NOT_NEEDED`(개인 세트에서 그 후보도 함께 채점한다).
- 점수는 같은 C 의 후보끼리 비교하는 값이다. 0.8 을 확률 80% 로 쓰지 않는다.

```sh
HF_HUB_OFFLINE=1 uv run --extra depth reply-ai depth-serve --port 18453
REPLY_AI_DEPTH_RANKING_ENABLED=true REPLY_AI_DEPTH_URL=http://127.0.0.1:18453 uv run reply-ai worker
HF_HUB_OFFLINE=1 uv run --extra depth pytest -m depth_live -s   # 실제 가중치 재현(OPENAI_API_KEY 가 있으면 한국어 변환 예시도)
```

### 예측·실측 연결과 추가 학습(`reply-ai depth …`)

추천 당시의 예측과 실제 대화 결과(Depth 라벨, `ai_reply.reply_depth_observation`)를 이어 평가 모델을 고칠 데이터를 만든다.
작업기·depth-serve 동작은 바꾸지 않는 오프라인 명령이다. `export-outcomes` 만 DB 를 읽고, 나머지는 파일만 쓴다.

1. `export-outcomes`: 실제 답장 1건 = 1행(jsonl) + 지표 요약.
   - 영어 번역 비용이 온라인 하루 한도(`REPLY_AI_DAILY_BUDGET_USD`)를 같이 쓴다. 운영·시연 시간대에는 실행하지 않는다.
   - 추천 쪽: `set_id`(답장의 유효 추천 출처, 없으면 그 답장 episode 가 본 세트), 노출 순서의 카드별 예문(한국어/영어)·추천 당시
     예측 점수·`model_id`(랭킹이 꺼져 있던 세트는 null), 선택한 카드 id(없으면 null).
   - 실제 쪽: 실제 전송 문장(수집 허용 출처만 — `episode_reply_snapshot`, `REPLY_AI_REAL_USER_FEEDBACK_ENABLED` 가 꺼져 있으면
     QA·SYNTHETIC 만, 그 밖의 답장은 행을 만들지 않고 `skipped_provenance` 로만 센다), 전송 시각(`reply_link.sent_at`), 그 문장의 영어,
     지금 지정한 모델로 다시 매긴 점수와 그 `model_id`, Width·Depth·관측 상태(UNOBSERVED 의 depth·width 는 null, 0 과 다르다),
     영상 작성자 재답장 여부(`owner_replied`)·후속 메시지 수(`followup_message_count`, 559 표에 그 칸이 없으면 null).
   - 재채점 모델: 세트에 기록된 `model_id`(카드 예측을 낸 모델)와 지금 모델이 같을 때만 점수를 남긴다. 다르면 점수 null +
     `score_status=MODEL_MISMATCH`(`set_model_id`·`scorer_model_id` 를 함께 남긴다). 추천 노출이 없거나 세트에 기록된 모델이 없으면
     지정한 모델(`--model` 또는 depth-serve)의 id 로 남긴다. 그 밖의 상태는 `NOT_SCORED`(점수 모델·영어 없음)·`SCORE_FAILED`.
   - 연결 구분: 노출 카드 예문과 실제 문장의 문자 유사도(difflib ratio, 공백 정규화) 최댓값으로 `CARD_AS_IS`(≥ `--as-is-threshold`
     0.9) / `CARD_EDITED`(≥ `--edited-threshold` 0.4) / `SELF_WRITTEN`, 추천 세트가 없으면 `NO_RECOMMENDATION`.
     세트는 있었지만 무효(revoked)·보존 기간 파기(카드 비움)·행 없음이면 노출 카드를 알 수 없으므로 `RECOMMENDATION_UNAVAILABLE`
     (`recommendation.set_status=UNAVAILABLE`, `exposed=null`)로 따로 센다. 임계값은 각 행과 요약에 남는다.
   - 공통 맥락 C: 영상마다 하나. 추천 당시 기록(`depth_ranking`, OK)의 영어 C, 없으면 분석 스냅샷에서 같은 규칙으로 만든 C 를
     영어로 바꾼다. 개인 기록 P 는 넣지 않는다. 영어 C(`context_en`)는 학습 입력이라 남는다.
   - 단체 대화 원문: 한국어 C 는 기본으로 넣지 않고 `context_ko_sha` 만 남긴다. `--include-context-text` 를 줄 때만 `context_ko` 에
     원문을 넣고 C 번역도 캐시 파일에 남긴다(기본은 답장 번역만 캐시). 내보낸 파일·캐시는 보존 기간 파기 대상이 아니므로 쓰고 나면 지운다.
   - 영어 변환: 카드를 그대로 보낸 답장은 그 카드의 영어를 쓴다. 나머지는 `depth_translate` 지시문·스키마로 변환하고 온라인 일일 한도로
     비용을 예약한다. `--translation-cache` 는 원문 sha256 → 영어만 담는다(한국어 원문 없음, 영어는 내용 그대로다). `--translate off` 면 캐시만 쓴다.
   - 점수: `--scorer serve`(지금 depth-serve 의 모델) | `local --model <허브 id 또는 train 결과 디렉터리>` | `none`.
   - 요약: 실제 문장 점수 vs 실제 Depth 의 Spearman(n 함께, 3개 미만·한쪽이 모두 같으면 null), 같은 C 쌍의 쌍 비교 정확도(같은 점수는
     `ties` 로 따로, `--max-hour-gap` 은 build-pairs 와 같다), 연결 구분별 평균 Depth·영상 작성자 재답장률·평균 후속 메시지 수(값을 아는 행만
     세고 없으면 null), 전체 재답장률, 점수 상태별 수, 미관측 수·이유, 관측 Depth 0 의 수.
2. `build-pairs`: export 결과 → 같은 C(한국어 C 턴 sha `context.context_ko_sha`, 번역 캐시 키와 같은 값)의 실제 답장끼리 depth 큰 쪽을
   r+ 로 한 (C, r+, r−). 같은 영상이라도 C 가 다르면 쌍을 만들지 않는다. 쌍마다 `context_ko_sha` 가 남는다. 답장은 언제나 실제 전송 문장이다
   (카드 예문은 분석용). 동점·`--min-depth-diff` 미만, 두 답장 전송 시각 차가 `--max-hour-gap`(기본 1시간, 논문 §3.1, 0 이면 보지 않음)을
   넘거나 전송 시각을 모르는 쌍은 빼고 이유별로 센다(unobserved·text_unavailable·english_unavailable·context_unavailable·
   context_key_unavailable·single_reply·tie·below_min_diff·over_hour_gap·sent_at_unknown·chat_room_unknown·cohort_unknown·chat_room_cohort_conflict·INPUT_COLLISION).
   - `INPUT_COLLISION`: 두 답장이 영어 변환·토큰 잘림 뒤 실제 점수 입력(θ0 토크나이저 + `depth_service.build_ids`, 같은 C·같은
     `--max-seq-len`·C 토큰 상한)이 같으면 비교할 수 없어 뺀다. 빠진 쌍 id 는 `input_collisions`, 쌍마다 `input_profile`·양쪽 `input_hash` 가 남는다.
   - 분할(기수 단위 3개): 행·쌍의 `cohort_id`(답장 채팅방의 3일 방 `rooms.cohort_id`, 수집기가 남긴다) 최댓값 = 최신 기수 = `test`.
     그 이전 기수 안에서 채팅방 id 해시(`--val-fraction`, 기본 0.2)로 `val`(조기 종료용)·`train`. `cohort_id` 를 모르는 쌍은 뺀다.
     같은 채팅방은 두 분할에 걸치지 않는다(기수가 엇갈리는 채팅방은 뺀다). `chat_room_id` 는 `reply_depth_observation.room_id`
     (= `chat_messages.chat_room_id`)이고 3일 방 `rooms.id` 가 아니다.
3. `train`: 시작점은 언제나 공개 θ0(`microsoft/DialogRPT-depth` 고정 revision)다. 이전 학습 결과에서 이어서 학습하지 않고, 매 기수 θ0 +
   누적 전체 쌍으로 다시 학습한다(다른 base·로컬 디렉터리는 거부, `--base`·`--revision` 인자 없음). 손실 L = −log σ(h(C,r+) − h(C,r−)),
   h = 답장 마지막 토큰의 logit(depth-serve 와 같은 입력·위치 계산, max_seq_len 50). 전체 가중치 + 점수 헤드, AdamW, `--lr` 기본 1e-5.
   `--eval-every`(기본 10) step 마다 val 쌍의 같은 손실을 재고 `--patience`(기본 3)번 연속 최저값보다 낮아지지 않으면 멈춘다
   (`--max-steps` 기본 2000 은 안전 상한). val 이 비면 거부한다. 저장하는 가중치는 최저 검증 손실 시점이다(step 0 = θ0 포함).
   결과 디렉터리에 `model.safetensors`·토크나이저·`depth_train.json`(base·revision·공식 출력 재현 점검, 인자, 멈춘 이유·best step,
   step 별 train 손실·검증 손실, 쌍 파일 sha, train/val 채팅방·기수). 비어 있지 않은 디렉터리에는 쓰지 않는다.
4. `eval`: test 분할(최신 기수, train/val 채팅방·기수와 겹치면 거부)에서 θ0 vs 새 모델의 쌍 비교 정확도. test 쌍이 `--min-eval-pairs`(기본 30)
   미만이면 `INCONCLUSIVE`(정확도는 참고로만 남긴다), 이상이고 새 모델 정확도가 θ0 보다 2%p 이상(≥ 0.02, 경계 포함) 높을 때만
   `REPLACEABLE`, 아니면 `KEEP_BASE`. 교체(depth-serve 가 읽는 모델 바꾸기)는 자동으로 하지 않는다.

- 모델 교체는 수동이다. `eval` 은 판정(`REPLACEABLE`/`KEEP_BASE`/`INCONCLUSIVE`)만 내고, depth-serve 가 읽는 모델 경로는 사람이 바꾼다.
- 비노출 답장 원문 저장, 장기 학습셋 보관, 단체방에서 같은 방향 카드 제외 3건은 처리방침 개정 전까지 보류다. 그때까지 `train` 의
  "누적 전체 쌍"은 원문 보존 기간 30일(`episode_reply_snapshot`) 안에 남아 있는 데이터만이다.

```sh
uv run reply-ai depth export-outcomes --out outcomes.jsonl --summary outcomes.summary.json \
    --scorer serve --translation-cache depth_translation_cache.jsonl
HF_HUB_OFFLINE=1 uv run --extra depth reply-ai depth build-pairs --outcomes outcomes.jsonl --out pairs.jsonl --val-fraction 0.2
HF_HUB_OFFLINE=1 uv run --extra depth reply-ai depth train --pairs pairs.jsonl --out depth-ft --eval-every 10 --patience 3 --batch-size 8
HF_HUB_OFFLINE=1 uv run --extra depth reply-ai depth eval --pairs pairs.jsonl --candidate depth-ft
```

GPT-2 medium 전체 미세조정이라 CPU 에서 메모리 약 6GB(가중치·기울기·AdamW 상태), 결과 가중치는 약 1.4GB 다. 결과 디렉터리는 저장소 밖에 둔다.

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

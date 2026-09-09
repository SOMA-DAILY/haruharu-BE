# Airflow — 하루하루 배치 지휘 (#390)

Apache Airflow 3.3.1 로 Spring Batch 잡을 **지휘**하는 파일럿. Airflow 가 "언제·다시·기한"을 맡고, Spring Batch 가 "실제 실행과 이력"을 맡는다.
첫 대상은 알림함 정리 잡(`notificationCleanupJob`)이다. Airflow 는 [#389 내부 배치 트리거 API](../app/src/main/java/org/son/haruharu/batch/api) 를 호출할 뿐 잡 로직을 모른다.

```text
04:00 KST ──▶ start_job : POST /internal/batch/jobs/notificationCleanupJob/executions {"bucket": "<KST 날짜>"}
                          retries 3 · 지수 백오프(30s→60s→120s, 최대 5m) — API 가 멱등이라 재시도가 안전
           ──▶ wait_job  : GET /internal/batch/executions/{id} 30초 폴링, 10분 상한 (reschedule 모드)
           Deadline     : logical_date(04:00) + 15분 = 04:15 까지 안 끝나면 deadline_missed (triggerer)
           실패         : on_failure_callback → 구조화 로그 + (선택) Slack Webhook
```

## 핵심 계약: `bucket` = Airflow `run_after`(KST 날짜) = Spring Batch 식별 파라미터

| Airflow | Spring Batch |
|---|---|
| DAG Run 의 `run_after` → KST 날짜 (`2026-09-05`) | `JobParameters{bucket=2026-09-05}` → `BATCH_JOB_INSTANCE (JOB_NAME, JOB_KEY)` 유니크 |
| 같은 날짜 재트리거 / HTTP 재시도 | `200 ALREADY_COMPLETED` + 기존 execution_id (잡은 한 번만 돈다) |
| 백필 `airflow backfill create --from-date 9/1 --to-date 9/3` | bucket 9/1·9/2·9/3 JobInstance 3개 생성 |
| 이전 실행 FAILED 인 날짜 재트리거 | `202 RESTARTED` (같은 JobInstance 에 새 JobExecution) |

at-least-once 트리거 + 멱등 잡 = exactly-once 효과.

## 로컬 실행

전제: Docker Desktop, 로컬 Spring 앱(#389 포함)이 `:8080` 에서 `INTERNAL_BATCH_TOKEN` 을 갖고 기동 중.

```bash
cd airflow
cp .env.example .env            # INTERNAL_BATCH_TOKEN 을 앱과 같은 값(32자 이상)으로, FERNET_KEY 생성
docker compose up airflow-init   # 메타DB 마이그레이션 + airflow/airflow 계정
docker compose up -d             # apiserver(:8081) · scheduler · dag-processor · triggerer
open http://localhost:8081       # airflow / airflow
```

DAG 는 생성 시 일시정지 상태다. UI 토글 또는 `docker compose exec airflow-scheduler airflow dags unpause notification_cleanup`.
unpause 하면 `catchup=False` 라도 **가장 최근 스케줄 구간 1개**는 즉시 생성된다(Airflow 기본 동작).

### 자주 쓰는 명령

```bash
# 수동 트리거 (logical_date 없음 → run_after = 지금 → bucket = 오늘 KST)
docker compose exec airflow-scheduler airflow dags trigger notification_cleanup
# 백필 (DAG 당 backfill 하나만 동시에 가능). --max-active-runs 1 을 권장하지만, 실제 직렬화는 Pool 이 강제한다(아래).
docker compose exec airflow-scheduler airflow backfill create --dag-id notification_cleanup \
  --from-date 2026-09-01T00:00:00+09:00 --to-date 2026-09-03T23:59:00+09:00 --reprocess-behavior completed --max-active-runs 1
# 테스트 (컨테이너 안에서 실행 — Airflow 의존성이 이미 있다)
docker compose run --rm --no-deps airflow-cli bash -c "pip install -q pytest && cd /opt/airflow && pytest -q tests"
# 로그
docker compose logs -f airflow-triggerer          # 데드라인 콜백
ls logs/dag_processor/<date>/dags-folder/         # DAG 실패 콜백(on_failure_callback 은 dag-processor 에서 실행)
ls logs/dag_id=notification_cleanup/run_id=*/     # 태스크 로그
```

REST API(v2)는 `POST /auth/token {username,password}` 로 JWT 를 받아 `Authorization: Bearer` 로 호출한다.
수동 트리거 `POST /api/v2/dags/{dag_id}/dagRuns` 는 body 에 `"logical_date": null` 이 필요하다.

> **로컬 전용 compose 다.** apiserver 는 `127.0.0.1:8081` 에만 바인딩되고 기본 계정은 `airflow/airflow` 다. `host.docker.internal` 로 호스트 전체에
> 닿으므로 신뢰된 개발 머신 밖(공유 VM·클라우드)에서 그대로 띄우지 않는다. JWT 시크릿은 `.env` 필수(없으면 기동 실패).

## 환경변수 (`.env.example`)

| 변수 | 용도 |
|---|---|
| `INTERNAL_BATCH_TOKEN` | 앱의 X-Internal-Token 과 같은 값. Connection `haruharu_app` 의 password 로 주입(`AIRFLOW_CONN_HARUHARU_APP`) |
| `HARUHARU_APP_BASE_URL` | 로컬은 `http://host.docker.internal:8080`. ECS 에선 Service Connect 주소 |
| `AIRFLOW__API_AUTH__JWT_SECRET` | Airflow 세션 JWT 서명 키. 필수 |
| `HARUHARU_ALERT_SLACK_WEBHOOK_URL` | 비우면 알림은 로그로만. 채우면 Slack Incoming Webhook 으로도 전송(필드 화이트리스트만 — 예외 원문·exit_message 는 싣지 않는다, 상세는 태스크 로그) |
| `HARUHARU_CLEANUP_DEADLINE_MINUTES` | 데드라인(기본 15). 로컬 실험 시 1~2 |
| `HARUHARU_ALERT_DEADLINE_INCLUDE_BACKFILL` | 기본 false. 백필 실행의 데드라인 초과(구조적으로 항상 초과)를 알릴지 |

## 알림은 어디까지 Airflow 가 하나

| 상황 | 감지 주체 |
|---|---|
| 태스크 실패(재시도 소진), DAG Run 실패 | Airflow `on_failure_callback` → `haruharu_alerts.dag_failed` |
| 04:15 까지 미완료 | Airflow Deadline Alert → `haruharu_alerts.deadline_missed` (triggerer) |
| **Airflow 스케줄러/triggerer 자체가 죽음** | Airflow 는 못 알린다. ECS 헬스체크(`/api/v2/monitor/health`) + CloudWatch, 또는 Datadog 로그 부재 감지 — 인프라 이슈 |

"감시자를 감시하는 건 한 단계 바깥에 둔다."

## 설계 메모 · 함정 (Airflow 3.3.1 기준)

- **Deadline 참조는 `DAGRUN_LOGICAL_DATE`.** SLA 마이그레이션 가이드가 `sla=` 를 여기에 대응시키고, "04:15 까지" 의미 그대로다.
  `DAGRUN_QUEUED_AT` 은 태스크 clear 시 Airflow 내부 재계산(`_recalculate_dagrun_queued_at_deadlines`)이 `interval` 을 dict 로 받아
  `TypeError` 로 죽는다(3.3.1). 수동 실행은 `logical_date` 가 None 이라 데드라인이 붙지 않는다 — 감시 대상은 스케줄 실행이다.
- **triggerer 는 `dags/` 를 import 경로에 올리지 않는다.** Deadline `AsyncCallback` 은 dotted path 로 import 되므로 `PYTHONPATH=/opt/airflow/dags` 를 준다(compose·Dockerfile).
- **Task SDK 의 `RuntimeTaskInstance` 에는 `.log` 가 없다.** 모듈 로거(`logging.getLogger(__name__)`)를 쓰면 태스크 로그로 흘러간다.
- **`dags/` 의 공용 모듈은 `.airflowignore`** 에 넣어 dag-processor 가 DAG 파일로 파싱하지 않게 한다.
- **DagBag 테스트는 `dagbag.dags[...]`** 로 읽는다. `get_dag()` 은 메타DB 를 조회한다.
- **동기 실행 API** 라 `start_job` 응답에 이미 COMPLETED 가 실려 있다. `wait_job` 은 지금은 1회 poke 로 끝나지만 비동기 전환 시 그대로 폴링한다.
- 백필은 DAG 당 하나만 동시에 돈다(`AlreadyRunningBackfill`). 그러나 **한 백필 안의 여러 날짜는 기본 10개까지 동시에 돈다** — DAG 의
  `max_active_runs=1` 은 백필에 적용되지 않는다. 그래서 `start_job` 에 슬롯 1 짜리 **Pool `haruharu_batch_trigger`** 를 걸어 어떤 경로로
  만들어진 run 이든 트리거는 한 번에 하나만 나가게 강제한다. Pool 은 `airflow-init` 이 만든다(운영 부트스트랩에도 필요 — 없으면 태스크가 scheduled 에 멈춘다).
- `wait_job` 의 조회 실패는 '아직 모름' 이다: 연결 오류·5xx·429·깨진 200 본문은 다음 poke 로 넘기고(10분 상한), 400/401/403/404 만 즉시 실패.
- `start_job` 의 200/202 응답이 깨져 있으면(execution_id 없음) 재시도한다 — 서버는 이미 잡을 시작했을 수 있고, 멱등 재호출이 ID 를 돌려준다.
- 데드라인 콜백은 triggerer 이벤트 루프에서 돈다. 동기 HTTP 는 `asyncio.to_thread` 로 내려 루프를 막지 않는다.
- 시크릿 스코프: 배치 토큰(Connection)은 태스크를 실행하는 scheduler(LocalExecutor)에만, Slack 웹훅은 콜백을 실행하는 dag-processor·triggerer 에만 주입한다. `.env` 는 compose 치환에만 쓰고 컨테이너에 통째로 넣지 않는다.

## 운영 배포(후속 인프라 이슈)

`Dockerfile` 이 `dags/` 를 이미지에 굽는다(Fargate 는 공유 볼륨이 없다). DAG 수정 = PR = 이미지 빌드 = 롤링 배포.
dev ECS Fargate 서비스 1개(api-server·scheduler·dag-processor·triggerer), 메타DB 는 dev RDS 의 `airflow` DB, 앱 호출은 Service Connect,
ALB 에서 `/internal/*` 403 차단이 배포 게이트.

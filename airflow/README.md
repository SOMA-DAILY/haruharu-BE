# Airflow — 하루하루 배치 지휘 (#390, #469)

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
| `HARUHARU_ALERT_SLACK_WEBHOOK_URL` | 비우면 알림은 로그로만. **Slack 또는 Discord** Incoming Webhook URL — URL 로 판별해 본문 형식(`text` / `content`, Discord 2000자)을 맞춘다(#411). 필드 화이트리스트만 전송(예외 원문·exit_message 는 싣지 않는다) |
| `HARUHARU_CLEANUP_DEADLINE_MINUTES` | 데드라인(기본 15). 로컬 실험 시 1~2 |
| `HARUHARU_ALERT_DEADLINE_INCLUDE_BACKFILL` | 기본 false. 백필 실행의 데드라인 초과(구조적으로 항상 초과)를 알릴지 |
| `HARUHARU_RECONCILE_ALERT_PERIOD_MINUTES` | `room_reconcile` 실패 알림 주기(기본 30). '마지막 성공 이후 이 분' 부터 이 간격마다 한 번. 로컬 실험 시 5~10 |

## 알림은 어디까지 Airflow 가 하나

| 상황 | 감지 주체 |
|---|---|
| 태스크 실패(재시도 소진), DAG Run 실패 | Airflow `on_failure_callback` → `haruharu_alerts.dag_failed` → 로그 + Slack/Discord 웹훅 |
| 04:15 까지 미완료 | Airflow Deadline Alert → `haruharu_alerts.deadline_missed` (triggerer) |
| `room_reconcile` 태스크 실패(재시도 소진) | 태스크 `on_failure_callback` → `haruharu_alerts.room_reconcile_task_failed` (scheduler) — 아래 억제 규칙 통과 시에만 웹훅 |
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
- 시크릿 스코프: 배치 토큰(Connection)은 태스크를 실행하는 scheduler(LocalExecutor)에만, Slack 웹훅은 콜백을 실행하는 dag-processor·triggerer·scheduler(`room_reconcile` 태스크 콜백, #469)에만 주입한다. `.env` 는 compose 치환에만 쓰고 컨테이너에 통째로 넣지 않는다.

## 두 번째 DAG: `room_reconcile` (#469, #502)

방 status 전이 + 최종선택 동시공개 resolve 잡(`roomReconcileJob`, #466)을 매분 트리거한다. GROUP 채팅 종료 확정을 빠르게 반영하도록 #502에서 5분 주기를 1분으로 줄였다.

```text
매분(KST) ──▶ start_job : POST .../jobs/roomReconcileJob/executions {"bucket": "2026-10-02T14:07"}
                         HTTP 10s, 시도당 실행 상한 15s, retries 1, 재시도 대기 상한 5s
             ──▶ wait_job  : GET .../executions/{id} 5초 폴링(HTTP 3s), 센서 timeout 10s, poke 실행 상한 5s (reschedule)
             실패          : 태스크 on_failure_callback → 억제 규칙 통과 시에만 웹훅 (데드라인 없음)
```

| 항목 | `notification_cleanup` | `room_reconcile` |
|---|---|---|
| 주기 | 매일 04:00 | 매분 (`* * * * *`) |
| bucket | KST 날짜 `yyyy-MM-dd` (`bucket_for`) | KST 분 단위 내림 `yyyy-MM-ddTHH:mm` (`minute_bucket_for`) |
| 앱 `@Scheduled` 런처 | **끔** (`NOTIFICATION_CLEANUP_SCHEDULER_ENABLED=false`) | **켜 둠** (`ROOM_RECONCILE_ENABLED=true`, `45 * * * * *`) — 매분 45초 폴백 |
| 실패 알림 | DAG 콜백, run 마다 | 태스크 콜백, 장애 지속 시 주기당 1건 |
| 데드라인 | logical_date + 15분 | 없음 — 설정상 실행·재시도 예산 55초를 테스트로 고정. 스케줄러·Pool 대기는 별도 |
| 백필 | 의미 있음(날짜별 정리) | 의미 없음(잡은 실제 시각으로 reconcile) — `catchup=False` |

**앱 cron 을 끄지 않는 이유.** 이 잡은 매칭 성사(MatchResult·DIRECT 채팅·성사 푸시)를 포함한다. 앱 cron 을 끄면 Airflow 장애가 곧
매칭 중단인데 Airflow 생존 감시는 아직 없다. 두 경로는 **같은 bucket 문자열**을 쓰므로 `BATCH_JOB_INSTANCE (JOB_NAME, JOB_KEY)` 유니크로
구간당 한 번만 실행된다. Airflow 가 정각 직후, 앱이 45초에 발화하니 평소엔 Airflow 가 실행하고 앱은 `ALREADY_COMPLETED` 로 스킵한다.
bucket 문자열이 한 글자라도 다르면 별개 JobInstance 가 되어 같은 구간이 두 번 돈다. 양쪽 테스트
(`RoomReconcileJobLauncherTest` ↔ `test_haruharu_batch.py`)가 [공유 CSV](../app/src/test/resources/batch/room-reconcile-minute-buckets.csv)를 읽어
5분 경계 밖인 `14:07`, 정각과 앱 실행 시각인 45초, KST 자정 전후의 문자열 일치를 확인한다. 재시도는 현재 시각 대신 원래 run의 `run_after`를 사용한다.

**실행 시간 예산.** 시작 태스크 2회 × `execution_timeout` 15초 + 재시도 대기 5초 + 센서 `timeout` 10초 + 마지막 폴링 대기 5초 + poke 5초 = 55초다.
센서는 poke 이후 timeout을 검사하므로, 제한 직전 미완료 응답이 오면 다음 폴링 대기와 poke 실행까지 포함한다.
HTTP timeout은 전체 실행 시간을 제한하지 않으므로 태스크 실행 상한도 지정한다. Pool·worker·센서 reschedule 대기와 스케줄러 지연은 별도여서
실제 DAG 종료가 1분 안에 이루어진다는 보장은 없다. 앞 run이 늦으면 `max_active_runs=1`로 다음 run이 기다리고 앱 폴백이 계속 동작한다.
동기 HTTP 호출이 타임아웃돼도 앱의 Batch 실행은 취소되지 않으며, 같은 bucket 재시도는 실행 중/완료 상태에 따라 처리된다.

**전환 시 확인.** `45 * * * * *`는 45초 간격이 아니라 매분 45초 실행이다. 앱 환경변수에 기존 `ROOM_RECONCILE_CRON=45 */5 * * * *`가
남아 있으면 새 기본값을 덮어쓰므로 함께 변경한다. Airflow cron과 bucket 함수를 함께 배포해야 하며, cron만 1분으로 바꾸고 5분 bucket을 유지하면
5분 안의 나머지 실행이 이미 완료된 JobInstance로 처리된다. 앱 폴백 활성화와 Airflow DAG의 unpause 상태도 유지한다.

**알림 억제 (상태 없음).** 1분 DAG 를 run 마다 알리면 앱 장애 1시간에 60건이다. 태스크 컨텍스트의 `prev_start_date_success`
(마지막 성공 run 의 시작 시각)로 `outage = 이번 logical_date − 그 시각` 을 구하고, `ti.get_previous_dagrun()` 으로 직전 run 의 outage 도 구해
`floor(outage / period)`(period 기본 30분)가 **직전 run 보다 커진 run** — period 의 새 배수를 처음 넘은 run — 에서만 알린다 →
장애 30·60·90분… 에 한 건씩. 좁은 시간 창(`outage % period < 5분`)으로 판단하면 run 이 밀려 10분 간격이 될 때 창을 계속 놓쳐
지속 장애를 영영 못 알린다(Codex 리뷰로 발견) — 경계 통과로 판단해 run 간격과 무관하게 주기마다 한 번은 반드시 알린다.
30분 안에 회복하면 알리지 않는다(그동안은 앱 cron 이 처리). 이전 성공이 아예 없으면 첫 실패와 이후 벽시계 period 경계마다,
수동 run(`run_type=manual`, logical_date 를 지정한 API 트리거 포함)은 항상 알린다. pendulum 날짜 차이(`Interval`)는 `%`·`//` 가
`AttributeError` 라 초 단위로만 계산한다. 이 값은 태스크 컨텍스트에만 있어서 **DAG 콜백이 아니라 태스크 콜백**이고,
태스크 콜백은 태스크를 실행한 프로세스(LocalExecutor = scheduler)에서 돌므로 **웹훅을 scheduler 에도 주입**해야 한다.

공통 HTTP 본문(`start_batch_job`·`poke_batch_execution`)과 Pool 은 `haruharu_tasks.py` 로 분리돼 두 DAG 가 공유한다.

## 운영 배포 (dev, #405)

`Dockerfile` 이 `dags/` 를 이미지에 굽는다(Fargate 는 공유 볼륨이 없다). DAG 수정 = PR = `image-build.yml` 이 `haruharu-airflow:{sha,develop}` 푸시 = `deploy-ecs.yml`(dev) 이 태스크 정의 새 리비전 + 롤링 배포.

| 리소스 | 이름 |
|---|---|
| ECS 서비스 / 태스크 정의 | `haruharu-airflow-svc-dev` / `haruharu-airflow-dev` (클러스터 `haruharu`, Fargate ARM64, 1 vCPU / 2 GB) |
| 컨테이너 | `airflow-init`(마이그레이션·UI 유저·Pool, 종료) → `airflow-apiserver`·`airflow-scheduler`·`airflow-dag-processor`·`airflow-triggerer` |
| 메타DB | dev RDS `haruharu-dev-db` 의 데이터베이스 `airflow` (전용 유저) |
| 시크릿 | Secrets Manager `haruharu/airflow-dev` — Connection JSON(`AIRFLOW_CONN_HARUHARU_APP`)·JWT·Fernet·DB URL·UI 비밀번호·(선택) Slack |
| app 호출 경로 | Service Connect 네임스페이스 `haruharu.local` → `http://app-dev.haruharu.local:8080` (VPC 내부) |
| 인터넷 차단 | ALB 443 리스너 우선순위 1: `/internal/*` → 403 고정 응답 (dev·prod 공통) |
| 보안그룹 | `haruharu-ecs-airflow-sg` — 인바운드는 bastion→8080 뿐. app SG 8080·RDS SG 5432 에 소스로 등록 |
| UI 접근 | 공개 안 함. bastion SSH 터널로 태스크 사설 IP:8080 |
| 로그 | CloudWatch `/ecs/haruharu-airflow-dev` (스트림 prefix = 컨테이너명) |

절차·롤백·감시 설정은 `haruharu-infra/airflow-dev-runbook.md`(레포 밖). prod 는 dev 안정 후 별도 이슈.

> **#469 배포 시:** `room_reconcile` 실패 알림은 scheduler 컨테이너에서 나간다. 태스크 정의의 `airflow-scheduler` 컨테이너 secrets 에
> `HARUHARU_ALERT_SLACK_WEBHOOK_URL`(dag-processor·triggerer 와 같은 키)을 추가해야 한다. 없으면 알림은 태스크 로그에만 남는다.

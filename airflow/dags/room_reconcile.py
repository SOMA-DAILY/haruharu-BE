"""방 status reconcile 배치를 Airflow 가 지휘하는 DAG(#469). 알림 정리(#390) 다음 두 번째 지휘 잡이다.

  매 5분(KST) ──▶ start_job : POST /internal/batch/jobs/roomReconcileJob/executions {bucket=KST 5분 내림 "yyyy-MM-ddTHH:mm"}
                               (HTTP 20s, retries 2, 지수 백오프 15s~ 상한 30s — 트리거 API 가 멱등이라 재시도가 안전)
                 ──▶ wait_job  : GET /internal/batch/executions/{id} 15초 폴링(HTTP 10s), 90초 상한 (reschedule 모드)
                 실패          : 태스크 on_failure_callback → 장애가 30분의 새 배수를 넘은 첫 run 에서만 알림(30분마다 1건)

**run 예산 ≤ 4분.** 최악(시도 3회 × HTTP 20s + 재시도 대기 2회 × 상한 30s + 센서 90s + 마지막 poke 10s = 220s)도 주기(5분)
안에 끝나야 max_active_runs=1 에서 다음 run 이 밀리지 않는다. 남는 1분은 스케줄링·큐 대기 여유다(테스트로 고정).
트리거 API 는 동기라 잡이 20초를 넘기면 클라이언트만 타임아웃되고 잡은 앱에서 계속 돈다 — 재시도는 409(실행 중) 를 받고,
끝까지 안 끝나면 이 run 은 실패하지만 잡 자체는 완료되며 다음 run·앱 cron 은 새 bucket 으로 진행한다.

**앱 cron 을 끄지 않는다(폴백).** 이 잡은 최종선택 resolve(매칭 성사·DIRECT 채팅·성사 푸시)를 포함한다. 알림 정리처럼 앱 cron 을 끄면
Airflow 장애 = 매칭 성사 중단인데, Airflow 생존 감시는 아직 없다. 그래서 앱 RoomReconcileJobLauncher(45초 */5)를 켜 둔다.
두 경로는 같은 5분 bucket 을 쓰므로 BATCH_JOB_INSTANCE (JOB_NAME, JOB_KEY) 유니크로 한쪽만 실행된다 — Airflow 는 정각 직후,
앱은 45초에 발화하니 평소엔 Airflow 가 실행하고 앱은 ALREADY_COMPLETED 로 스킵한다(Airflow 주·앱 폴백).
반대로 한쪽이 FAILED 를 남기면 다른 쪽이 같은 bucket 으로 Spring Batch 재시작(RESTARTED)을 일으켜 복구한다.

**백필하지 않는다.** 잡은 bucket 이 아니라 실제 시각으로 reconcile 한다 — 과거 bucket 을 돌려도 '지금' sweep 을 한 번 더 할 뿐이다.

**데드라인을 걸지 않는다.** DeadlineAlert 는 run 마다 따로 걸려 위 알림 억제를 우회한다(앱 장애 시 5분마다 알림).
run 시간 상한은 위 run 예산이 이미 보장한다.
"""
from __future__ import annotations

from datetime import timedelta

import pendulum
from airflow.sdk import PokeReturnValue, dag, get_current_context, task

import haruharu_alerts
from haruharu_batch import ROOM_RECONCILE_JOB_NAME, five_minute_bucket_for
from haruharu_tasks import TRIGGER_POOL, poke_batch_execution, start_batch_job

START_HTTP_TIMEOUT_SECONDS = 20
POKE_HTTP_TIMEOUT_SECONDS = 10


@dag(
    dag_id="room_reconcile",
    description="방 status 전이·최종선택 resolve(Spring Batch roomReconcileJob)를 Airflow 가 5분마다 트리거·감시한다 (#469)",
    schedule="*/5 * * * *",
    start_date=pendulum.datetime(2026, 10, 1, tz="Asia/Seoul"),
    catchup=False,               # 과거 bucket 은 의미가 없다(위 '백필하지 않는다')
    max_active_runs=1,           # 앞 run 이 늦으면 다음 run 은 기다린다. 그 사이 구간은 앱 cron 폴백이 처리한다
    default_args={
        "owner": "haruharu-be",
        # 태스크 레벨 콜백이어야 한다 — 억제 판단에 쓰는 prev_start_date_success 는 태스크 컨텍스트에만 있다.
        # (DAG 레벨 on_failure_callback 은 dag-processor 에서 돌고 그 값이 없다.) 태스크 콜백은 태스크를 실행한 프로세스
        # (LocalExecutor = scheduler 컨테이너)에서 돌므로 웹훅 URL 이 scheduler 에도 주입돼 있어야 한다.
        "on_failure_callback": haruharu_alerts.room_reconcile_task_failed,
    },
    tags=["haruharu", "spring-batch", "room"],
)
def room_reconcile():

    @task(
        pool=TRIGGER_POOL,
        retries=2,
        retry_delay=timedelta(seconds=15),
        retry_exponential_backoff=True,
        max_retry_delay=timedelta(seconds=30),   # 지수 백오프에 해시 지터(최대 2배)가 붙으므로 상한으로 예산을 묶는다
    )
    def start_job() -> int:
        """잡 시작. 응답의 execution_id 를 XCom 으로 넘긴다. 앱 cron 이 먼저 돌았으면 200 ALREADY_COMPLETED 로 성공한다."""
        context = get_current_context()
        dag_run = context["dag_run"]
        bucket = five_minute_bucket_for(
            run_after=getattr(dag_run, "run_after", None),
            data_interval_end=context.get("data_interval_end"),
            logical_date=context.get("logical_date"),
        )
        return start_batch_job(ROOM_RECONCILE_JOB_NAME, bucket, timeout_seconds=START_HTTP_TIMEOUT_SECONDS)

    # 트리거 API 는 동기 실행이라 보통 첫 poke 에서 이미 종료 상태다. 센서는 비동기 전환 대비 + 상태 확인용.
    @task.sensor(poke_interval=15, timeout=90, mode="reschedule", retries=0)
    def wait_job(execution_id: int) -> PokeReturnValue:
        """COMPLETED 까지 폴링. FAILED 등 실패 종료면 즉시 실패(재시도 없음 — 잡은 이미 끝났고, 다음 주기·앱 cron 이 재시작한다)."""
        return poke_batch_execution(execution_id, timeout_seconds=POKE_HTTP_TIMEOUT_SECONDS)

    wait_job(start_job())


room_reconcile()

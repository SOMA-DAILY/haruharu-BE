"""알림함 정리 배치를 Airflow 가 지휘하는 파일럿 DAG(#390).

  04:00 KST ──▶ start_job : POST /internal/batch/jobs/notificationCleanupJob/executions {bucket=KST 날짜}
                             (retries 3, 지수 백오프 — 트리거 API 가 멱등이라 재시도가 안전)
                ──▶ wait_job  : GET  /internal/batch/executions/{id} 를 30초마다 폴링, 10분 상한 (reschedule 모드)
                Deadline     : 04:00(logical_date) + 15분 = 04:15 까지 안 끝나면 deadline_missed 콜백 (triggerer 에서 실행)
                실패         : on_failure_callback → 구조화 로그 + (선택) Slack

Airflow 는 Spring Batch 를 대체하지 않고 지휘한다. 잡의 실행·이력·멱등 좌표계는 BATCH_* 테이블(JobRepository)에 있고,
이 DAG 의 bucket 이 그 식별 파라미터와 1:1 이다. 앱 쪽 @Scheduled 런처는 NOTIFICATION_CLEANUP_SCHEDULER_ENABLED=false 로 끈다.
"""
from __future__ import annotations

import logging
import os
from datetime import timedelta

import pendulum
import requests
from airflow.sdk import (
    AsyncCallback,
    Connection,
    DeadlineAlert,
    DeadlineReference,
    PokeReturnValue,
    dag,
    get_current_context,
    task,
)
from airflow.sdk.exceptions import AirflowException, AirflowFailException

import haruharu_alerts
from haruharu_batch import (
    JOB_NAME,
    bucket_for,
    build_start_request,
    build_status_url,
    classify_status_http_failure,
    interpret_start_response,
    interpret_status_response,
)

log = logging.getLogger(__name__)  # Task SDK 의 RuntimeTaskInstance 에는 .log 가 없다 — 모듈 로거가 태스크 로그로 흘러간다

APP_CONN_ID = "haruharu_app"          # AIRFLOW_CONN_HARUHARU_APP 환경변수로 정의(docker-compose.yaml)
# 슬롯 1 짜리 Pool — 백필·수동·스케줄 실행이 겹쳐도 start_job 은 한 번에 하나만 돈다.
# max_active_runs=1 은 스케줄 실행에만 적용되고 Airflow 3 백필 동시성(기본 10)은 제어하지 못한다. Pool 은 그와 무관하게 강제된다.
# Pool 은 airflow-init(compose) / 운영 부트스트랩에서 `airflow pools set` 으로 만든다 — 없으면 태스크가 영원히 scheduled 에 머문다.
TRIGGER_POOL = "haruharu_batch_trigger"
HTTP_TIMEOUT_SECONDS = 30
# 데드라인(분). 운영 기본 15. 로컬 검증에서 콜백을 빨리 보려고 환경변수로 줄일 수 있다.
DEADLINE_MINUTES = int(os.environ.get("HARUHARU_CLEANUP_DEADLINE_MINUTES", "15"))


def _app_connection() -> tuple[str, str]:
    conn = Connection.get(APP_CONN_ID)
    host = conn.host or ""
    base_url = host if host.startswith("http") else f"http://{host}"
    if conn.port and f":{conn.port}" not in base_url:
        base_url = f"{base_url}:{conn.port}"
    return base_url, conn.password or ""


@dag(
    dag_id="notification_cleanup",
    description="알림함 30일 보관 정리(Spring Batch notificationCleanupJob)를 Airflow 가 트리거·감시한다 (#390)",
    schedule="0 4 * * *",
    start_date=pendulum.datetime(2026, 9, 1, tz="Asia/Seoul"),
    catchup=False,               # 백필은 의도적으로만: airflow backfill create --dag-id notification_cleanup --max-active-runs 1 ...
    max_active_runs=1,           # 스케줄 실행은 하나씩. 백필까지 직렬화하는 건 TRIGGER_POOL (JobRepository 유니크가 최종 방어)
    default_args={"owner": "haruharu-be"},
    on_failure_callback=haruharu_alerts.dag_failed,
    # 데드라인 = logical_date(스케줄 발화 시각, 04:00 KST) + N분 → "04:15 까지 끝나야 한다"는 SLA 의미 그대로.
    # DAGRUN_QUEUED_AT 을 쓰지 않는 이유: (1) SLA 마이그레이션 가이드가 sla= 를 LOGICAL_DATE 로 대응시킨다,
    # (2) Airflow 3.3.1 에서 QUEUED_AT 데드라인이 있는 DAG 는 태스크 clear 시 내부 재계산 버그
    #     (_recalculate_dagrun_queued_at_deadlines: interval 이 dict 로 역직렬화)로 실패한다.
    # 수동 실행은 logical_date 가 None 이라 데드라인이 붙지 않는다 — 감시 대상은 스케줄·백필 실행이다.
    deadline=DeadlineAlert(
        reference=DeadlineReference.DAGRUN_LOGICAL_DATE,
        interval=timedelta(minutes=DEADLINE_MINUTES),
        callback=AsyncCallback(
            "haruharu_alerts.deadline_missed",
            kwargs={"dag_id": "notification_cleanup", "deadline_minutes": DEADLINE_MINUTES},
        ),
    ),
    tags=["haruharu", "spring-batch", "notification"],
)
def notification_cleanup():

    @task(
        pool=TRIGGER_POOL,
        retries=3,
        retry_delay=timedelta(seconds=30),
        retry_exponential_backoff=True,
        max_retry_delay=timedelta(minutes=5),
    )
    def start_job() -> int:
        """잡 시작. 응답의 execution_id 를 XCom 으로 넘긴다. 멱등 재호출(200)도 성공이다."""
        context = get_current_context()
        dag_run = context["dag_run"]
        bucket = bucket_for(
            run_after=getattr(dag_run, "run_after", None),
            data_interval_end=context.get("data_interval_end"),
            logical_date=context.get("logical_date"),
        )
        base_url, token = _app_connection()
        url, headers, body = build_start_request(base_url, token, JOB_NAME, bucket)
        try:
            response = requests.post(url, headers=headers, json=body, timeout=HTTP_TIMEOUT_SECONDS)
        except requests.RequestException as e:
            # 앱이 내려가 있거나 네트워크 오류 — 재시도 대상(지수 백오프). 앱이 올라오면 같은 bucket 으로 멱등 재호출된다.
            raise AirflowException(f"배치 트리거 API 연결 실패: {e}") from e
        payload = _json_or_empty(response)
        execution_id = interpret_start_response(response.status_code, payload)
        log.info("배치 시작 응답: http=%s bucket=%s execution_id=%s outcome=%s",
                 response.status_code, bucket, execution_id, (payload.get("data") or {}).get("outcome"))
        return execution_id

    @task.sensor(poke_interval=30, timeout=600, mode="reschedule", retries=0)
    def wait_job(execution_id: int) -> PokeReturnValue:
        """COMPLETED 까지 폴링. FAILED 등 실패 종료면 즉시 실패(재시도 없음 — 잡은 이미 끝났다).

        조회 자체의 일시 장애(연결 오류·5xx·429)는 실패가 아니라 '아직 모름' 이다 — 다음 poke 로 넘기고 timeout(10분)이 상한이다.
        """
        base_url, token = _app_connection()
        try:
            response = requests.get(
                build_status_url(base_url, execution_id),
                headers={"X-Internal-Token": token, "Accept": "application/json"},
                timeout=HTTP_TIMEOUT_SECONDS,
            )
        except requests.RequestException as e:
            # 일시적 네트워크 장애로 오케스트레이션을 실패시키지 않는다 — 잡은 앱에서 계속 돌고 있을 수 있다. 다음 poke 에서 다시 본다.
            log.warning("상태 조회 연결 실패(다음 poke 에서 재시도): %s", e)
            return PokeReturnValue(is_done=False)
        if response.status_code != 200:
            if classify_status_http_failure(response.status_code) == "fail":
                raise AirflowFailException(f"상태 조회 거부(재시도 무의미): http={response.status_code}")
            log.warning("상태 조회 일시 실패(다음 poke 에서 재시도): http=%s", response.status_code)
            return PokeReturnValue(is_done=False)
        try:
            body = response.json()
        except ValueError:
            log.warning("상태 조회 본문이 JSON 이 아니다(다음 poke 에서 재시도)")
            return PokeReturnValue(is_done=False)
        decision = interpret_status_response(body)
        return PokeReturnValue(is_done=decision.is_done, xcom_value=decision.status)

    wait_job(start_job())


def _json_or_empty(response: requests.Response) -> dict:
    try:
        return response.json() or {}
    except ValueError:
        return {}


notification_cleanup()

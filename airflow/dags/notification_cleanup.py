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

import os
from datetime import timedelta

import pendulum
from airflow.sdk import (
    AsyncCallback,
    DeadlineAlert,
    DeadlineReference,
    PokeReturnValue,
    dag,
    get_current_context,
    task,
)

import haruharu_alerts
from haruharu_batch import NOTIFICATION_CLEANUP_JOB_NAME, bucket_for
from haruharu_tasks import TRIGGER_POOL, poke_batch_execution, start_batch_job

# 데드라인(분). 운영 기본 15. 로컬 검증에서 콜백을 빨리 보려고 환경변수로 줄일 수 있다.
DEADLINE_MINUTES = int(os.environ.get("HARUHARU_CLEANUP_DEADLINE_MINUTES", "15"))


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
        return start_batch_job(NOTIFICATION_CLEANUP_JOB_NAME, bucket)

    @task.sensor(poke_interval=30, timeout=600, mode="reschedule", retries=0)
    def wait_job(execution_id: int) -> PokeReturnValue:
        """COMPLETED 까지 폴링. FAILED 등 실패 종료면 즉시 실패(재시도 없음 — 잡은 이미 끝났다).

        조회 자체의 일시 장애(연결 오류·5xx·429)는 실패가 아니라 '아직 모름' 이다 — 다음 poke 로 넘기고 timeout(10분)이 상한이다.
        """
        return poke_batch_execution(execution_id)

    wait_job(start_job())


notification_cleanup()

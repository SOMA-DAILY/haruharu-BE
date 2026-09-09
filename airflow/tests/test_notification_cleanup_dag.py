"""notification_cleanup DAG 구조 테스트(#390). import 오류·태스크 의존·스케줄·재시도·데드라인 설정을 고정한다."""
import os
from datetime import timedelta
from pathlib import Path

import pytest
from airflow.models import DagBag

DAGS_DIR = Path(__file__).resolve().parent.parent / "dags"


@pytest.fixture(scope="module")
def dagbag():
    return DagBag(dag_folder=str(DAGS_DIR))  # Airflow 3.3: include_examples 인자 없음, LOAD_EXAMPLES=false 로 제어


@pytest.fixture(scope="module")
def dag(dagbag):
    return dagbag.dags["notification_cleanup"]  # get_dag 은 메타DB 를 조회한다 — 파싱 결과 dict 를 직접 쓴다


def test_no_import_errors(dagbag):
    assert dagbag.import_errors == {}
    assert "notification_cleanup" in dagbag.dag_ids


def test_tasks_and_dependency(dag):
    assert sorted(dag.task_ids) == ["start_job", "wait_job"]
    assert dag.get_task("start_job").downstream_task_ids == {"wait_job"}
    assert dag.get_task("wait_job").upstream_task_ids == {"start_job"}


def test_schedule_timezone_catchup(dag):
    timetable = dag.timetable
    expression = getattr(timetable, "_expression", None) or getattr(timetable, "expression", None) or timetable.description
    assert "0 4 * * *" in str(expression)
    assert str(dag.timezone) == "Asia/Seoul"
    assert dag.catchup is False
    assert dag.max_active_runs == 1


def test_start_job_serialized_by_pool(dag):
    # max_active_runs 는 백필 동시성을 못 막는다 — Pool 슬롯 1 이 실제 강제 장치다.
    assert dag.get_task("start_job").pool == "haruharu_batch_trigger"


def test_start_job_retry_policy(dag):
    start = dag.get_task("start_job")
    assert start.retries == 3
    assert start.retry_exponential_backoff is True
    assert start.retry_delay == timedelta(seconds=30)


def test_wait_job_is_reschedule_sensor(dag):
    wait = dag.get_task("wait_job")
    assert wait.poke_interval == 30
    assert wait.timeout == 600
    assert wait.mode == "reschedule"
    assert wait.retries == 0  # 잡이 FAILED 면 센서 재시도는 무의미


def test_deadline_alert_15_minutes_after_logical_date(dag):
    deadline = dag.deadline
    alerts = deadline if isinstance(deadline, list) else [deadline]
    assert len(alerts) == 1
    alert = alerts[0]
    # 운영 기본 15분. 로컬 .env 로 줄여 검증할 수 있으므로 환경변수를 기준으로 비교한다.
    expected_minutes = int(os.environ.get("HARUHARU_CLEANUP_DEADLINE_MINUTES", "15"))
    assert alert.interval == timedelta(minutes=expected_minutes)
    # LOGICAL_DATE 기준(= 04:00 KST + 15분). QUEUED_AT 은 3.3.1 clear 버그 + SLA 의미와 어긋나 쓰지 않는다.
    ref = f"{type(alert.reference).__name__} {alert.reference!r}".lower()
    assert "logical" in ref and "queued" not in ref


def test_failure_callback_registered(dag):
    assert dag.on_failure_callback is not None

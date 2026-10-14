"""room_reconcile DAG 구조 테스트(#469). 1분 주기·재시도/센서 상한·Pool·알림 콜백 배선을 고정한다."""
from datetime import timedelta
from pathlib import Path
from types import SimpleNamespace

import pendulum
import pytest
from airflow.models import DagBag
from airflow.serialization.serialized_objects import DagSerialization
from airflow.timetables.base import DataInterval, TimeRestriction

import haruharu_alerts
import room_reconcile

DAGS_DIR = Path(__file__).resolve().parent.parent / "dags"
SCHEDULE_INTERVAL = timedelta(minutes=1)


@pytest.fixture(scope="module")
def dagbag():
    return DagBag(dag_folder=str(DAGS_DIR))


@pytest.fixture(scope="module")
def dag(dagbag):
    return dagbag.dags["room_reconcile"]


def test_no_import_errors(dagbag):
    assert dagbag.import_errors == {}
    assert {"room_reconcile", "notification_cleanup"} <= set(dagbag.dag_ids)


def test_tasks_and_dependency(dag):
    assert sorted(dag.task_ids) == ["start_job", "wait_job"]
    assert dag.get_task("start_job").downstream_task_ids == {"wait_job"}


def test_every_minute_kst_without_catchup(dag):
    timetable = dag.timetable
    expression = getattr(timetable, "_expression", None) or getattr(timetable, "expression", None) or timetable.description
    assert str(expression) == "* * * * *"
    assert str(dag.timezone) == "Asia/Seoul"
    # 과거 bucket 백필은 의미가 없다 — 잡은 bucket 이 아니라 실제 시각으로 reconcile 한다
    assert dag.catchup is False
    assert dag.max_active_runs == 1


def test_start_job_shares_trigger_pool(dag):
    # notification_cleanup 과 같은 슬롯 1 Pool — 두 잡의 launch 가 겹쳐 JobRepository 직렬화 충돌이 나지 않게 줄 세운다
    assert dag.get_task("start_job").pool == "haruharu_batch_trigger"


def test_timetable_advances_one_minute_even_outside_five_minute_boundary(dag):
    previous = pendulum.datetime(2026, 10, 2, 14, 6, tz="Asia/Seoul")
    # SDK DAG 정의를 직렬화한 뒤 스케줄러가 읽는 실제 timetable로 다음 실행 시각을 계산한다.
    scheduler_dag = DagSerialization.from_dict(DagSerialization.to_dict(dag))
    next_run = scheduler_dag.timetable.next_dagrun_info(
        last_automated_data_interval=DataInterval.exact(previous),
        restriction=TimeRestriction(earliest=previous, latest=None, catchup=True),
    )
    assert next_run.run_after == previous.add(minutes=1)


def test_start_job_retries_keep_original_run_minute_bucket(dag, monkeypatch):
    run_after = pendulum.datetime(2026, 10, 2, 14, 7, tz="Asia/Seoul")
    context = {"dag_run": SimpleNamespace(run_after=run_after),
               "data_interval_end": run_after.subtract(minutes=1), "logical_date": None}
    start = dag.get_task("start_job").python_callable
    calls = []
    monkeypatch.setitem(start.__globals__, "get_current_context", lambda: context)

    def capture(job_name, bucket, timeout_seconds):
        calls.append((job_name, bucket, timeout_seconds))
        return 123

    monkeypatch.setitem(start.__globals__, "start_batch_job", capture)
    assert start() == 123
    assert start() == 123
    assert calls == [("roomReconcileJob", "2026-10-02T14:07", room_reconcile.START_HTTP_TIMEOUT_SECONDS)] * 2


def test_retry_and_sensor_policy(dag):
    start = dag.get_task("start_job")
    wait = dag.get_task("wait_job")
    assert start.retries == 1
    assert start.retry_exponential_backoff  # 직렬화 후엔 True 가 2.0 으로 보정된다 — 어느 쪽이든 지수 백오프
    assert wait.mode == "reschedule"
    assert wait.retries == 0


def test_execution_retry_budget_fits_within_schedule_interval(dag):
    """실행·재시도 예산을 55초 이하로 제한한다. Pool·worker·reschedule 대기는 별도다.

    requests timeout은 전체 실행 제한이 아니므로 execution_timeout으로 시도 전체를 제한한다.
    센서는 poke 이후 timeout을 검사하므로 제한 직전 false 응답이면 다음 폴링 대기와 poke 실행까지 포함한다.
    """
    start = dag.get_task("start_job")
    wait = dag.get_task("wait_job")
    attempts = start.retries + 1
    assert start.execution_timeout is not None
    assert wait.execution_timeout is not None
    assert room_reconcile.START_HTTP_TIMEOUT_SECONDS < start.execution_timeout.total_seconds()
    assert room_reconcile.POKE_HTTP_TIMEOUT_SECONDS < wait.execution_timeout.total_seconds()
    start_worst = attempts * start.execution_timeout.total_seconds() + start.retries * start.max_retry_delay.total_seconds()
    wait_worst = wait.timeout + wait.poke_interval + wait.execution_timeout.total_seconds()
    assert start_worst + wait_worst <= (SCHEDULE_INTERVAL - timedelta(seconds=5)).total_seconds()


def test_failure_alert_is_task_level_and_throttled(dag):
    # 억제 판단에 쓰는 prev_start_date_success 는 태스크 컨텍스트에만 있다 — DAG 레벨 콜백(dag-processor)엔 없다.
    for task_id in ("start_job", "wait_job"):
        callbacks = dag.get_task(task_id).on_failure_callback
        callbacks = callbacks if isinstance(callbacks, list) else [callbacks]
        assert haruharu_alerts.room_reconcile_task_failed in callbacks
    # DAG 레벨 콜백까지 걸면 run 마다 억제 없이 한 번 더 알린다
    assert not dag.on_failure_callback


def test_no_deadline_alert(dag):
    # 데드라인은 run 마다 걸려 억제 규칙을 우회한다. 각 시도 실행과 센서 대기는 별도 timeout으로 제한한다.
    assert not dag.deadline

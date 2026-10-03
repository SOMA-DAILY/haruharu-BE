"""room_reconcile DAG 구조 테스트(#469). 5분 주기·재시도/센서 상한·Pool·알림 콜백 배선을 고정한다."""
from datetime import timedelta
from pathlib import Path

import pytest
from airflow.models import DagBag

import haruharu_alerts
import room_reconcile

DAGS_DIR = Path(__file__).resolve().parent.parent / "dags"
SCHEDULE_INTERVAL = timedelta(minutes=5)


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


def test_every_five_minutes_kst_without_catchup(dag):
    timetable = dag.timetable
    expression = getattr(timetable, "_expression", None) or getattr(timetable, "expression", None) or timetable.description
    assert "*/5 * * * *" in str(expression)
    assert str(dag.timezone) == "Asia/Seoul"
    # 과거 bucket 백필은 의미가 없다 — 잡은 bucket 이 아니라 실제 시각으로 reconcile 한다
    assert dag.catchup is False
    assert dag.max_active_runs == 1


def test_start_job_shares_trigger_pool(dag):
    # notification_cleanup 과 같은 슬롯 1 Pool — 두 잡의 launch 가 겹쳐 JobRepository 직렬화 충돌이 나지 않게 줄 세운다
    assert dag.get_task("start_job").pool == "haruharu_batch_trigger"


def test_retry_and_sensor_policy(dag):
    start = dag.get_task("start_job")
    wait = dag.get_task("wait_job")
    assert start.retries == 2
    assert start.retry_exponential_backoff  # 직렬화 후엔 True 가 2.0 으로 보정된다 — 어느 쪽이든 지수 백오프
    assert wait.mode == "reschedule"
    assert wait.retries == 0


def test_worst_case_run_fits_within_schedule_interval(dag):
    """run 하나의 최악 소요가 주기(5분) 안에 들어와야 max_active_runs=1 에서 다음 run 이 밀리지 않는다(Codex 리뷰).

    start_job: 시도마다 HTTP 타임아웃까지 걸리고, 재시도 대기는 지수 백오프에 해시 지터(최대 2배)가 붙되 max_retry_delay 가 상한이다.
    wait_job : 센서 timeout 직전에 시작한 마지막 poke 가 HTTP 타임아웃까지 걸리는 경우.
    스케줄링·큐 대기 여유로 1분을 남긴다(합계 ≤ 4분).
    """
    start = dag.get_task("start_job")
    wait = dag.get_task("wait_job")
    attempts = start.retries + 1
    start_worst = attempts * room_reconcile.START_HTTP_TIMEOUT_SECONDS + start.retries * start.max_retry_delay.total_seconds()
    wait_worst = wait.timeout + room_reconcile.POKE_HTTP_TIMEOUT_SECONDS
    assert start_worst + wait_worst <= (SCHEDULE_INTERVAL - timedelta(minutes=1)).total_seconds()


def test_failure_alert_is_task_level_and_throttled(dag):
    # 억제 판단에 쓰는 prev_start_date_success 는 태스크 컨텍스트에만 있다 — DAG 레벨 콜백(dag-processor)엔 없다.
    for task_id in ("start_job", "wait_job"):
        callbacks = dag.get_task(task_id).on_failure_callback
        callbacks = callbacks if isinstance(callbacks, list) else [callbacks]
        assert haruharu_alerts.room_reconcile_task_failed in callbacks
    # DAG 레벨 콜백까지 걸면 run 마다 억제 없이 한 번 더 알린다
    assert not dag.on_failure_callback


def test_no_deadline_alert(dag):
    # 데드라인은 run 마다 걸려 억제 규칙을 우회한다. 실행 시간 상한은 HTTP 타임아웃·재시도·센서 timeout 이 보장한다.
    assert not dag.deadline

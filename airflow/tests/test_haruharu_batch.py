"""haruharu_batch 순수 함수 단위 테스트(#390). Airflow 런타임 없이 bucket 변환·응답 해석·요청 구성을 검증한다."""
import re
import csv
from pathlib import Path
from datetime import datetime, timezone

import pytest
from airflow.sdk.exceptions import AirflowException, AirflowFailException

from haruharu_batch import (
    NOTIFICATION_CLEANUP_JOB_NAME as JOB_NAME,
    ROOM_RECONCILE_JOB_NAME,
    bucket_for,
    minute_bucket_for,
    build_start_request,
    classify_status_http_failure,
    interpret_start_response,
    interpret_status_response,
)


class TestBucketFor:
    def test_kst_pre_midnight_stays_same_kst_day(self):
        # 23:30 KST = 14:30 UTC 같은 날. 자정 직전도 KST 날짜 그대로.
        assert bucket_for(run_after=datetime(2026, 9, 4, 14, 30, tzinfo=timezone.utc)) == "2026-09-04"

    def test_kst_boundary_utc_evening_is_next_kst_day(self):
        # 04:00 KST = 전날 19:00 UTC. bucket 은 KST 날짜여야 Spring 런처(LocalDate.now(Asia/Seoul))와 같은 좌표계가 된다.
        run_after = datetime(2026, 9, 4, 19, 0, tzinfo=timezone.utc)
        assert bucket_for(run_after=run_after) == "2026-09-05"

    def test_prefers_run_after_over_other_dates(self):
        run_after = datetime(2026, 9, 5, 4, 0, tzinfo=timezone.utc)
        interval_end = datetime(2026, 9, 1, 0, 0, tzinfo=timezone.utc)
        assert bucket_for(run_after=run_after, data_interval_end=interval_end) == "2026-09-05"

    def test_falls_back_to_data_interval_end_then_logical_date(self):
        interval_end = datetime(2026, 9, 2, 0, 0, tzinfo=timezone.utc)
        logical = datetime(2026, 9, 1, 0, 0, tzinfo=timezone.utc)
        assert bucket_for(run_after=None, data_interval_end=interval_end, logical_date=logical) == "2026-09-02"
        assert bucket_for(run_after=None, data_interval_end=None, logical_date=logical) == "2026-09-01"

    def test_raises_when_no_date_available(self):
        with pytest.raises(AirflowFailException):
            bucket_for(run_after=None, data_interval_end=None, logical_date=None)


class TestMinuteBucketFor:
    """roomReconcileJob(#469) 의 분 단위 bucket. 앱 RoomReconcileJobLauncher 와 같은 문자열이어야 두 경로가 bucket 당 1회로 수렴한다.

    앱(Java)은 KST LocalDateTime 을 분 단위로 내림해 toString() 한다 — 초가 0 이라 ``2026-10-02T14:05`` (초 없음).
    RoomReconcileJobLauncherTest 가 14:07:45 KST → "2026-10-02T14:07" 를 고정하므로 여기서도 같은 시각·같은 문자열로 고정한다.
    """

    def test_matches_app_launcher_contract(self):
        # 14:07:45 KST = 05:07:45 UTC → 앱 런처 bucket 과 같은 "2026-10-02T14:07"
        assert minute_bucket_for(run_after=datetime(2026, 10, 2, 5, 7, 45, tzinfo=timezone.utc)) == "2026-10-02T14:07"

    def test_scheduled_run_on_boundary_is_that_boundary(self):
        # 스케줄 run 은 run_after = 정확히 분 경계(CronTriggerTimetable). 14:05:00 KST → 14:05
        assert minute_bucket_for(run_after=datetime(2026, 10, 2, 5, 5, 0, tzinfo=timezone.utc)) == "2026-10-02T14:05"

    def test_one_second_before_boundary_is_previous_bucket(self):
        assert minute_bucket_for(run_after=datetime(2026, 10, 2, 5, 4, 59, tzinfo=timezone.utc)) == "2026-10-02T14:04"

    def test_kst_midnight_crosses_utc_day(self):
        # 00:02 KST = 전날 15:02 UTC — 날짜도 KST 기준이어야 한다
        assert minute_bucket_for(run_after=datetime(2026, 10, 1, 15, 2, 0, tzinfo=timezone.utc)) == "2026-10-02T00:02"

    def test_falls_back_like_daily_bucket(self):
        interval_end = datetime(2026, 10, 2, 5, 10, tzinfo=timezone.utc)
        assert minute_bucket_for(run_after=None, data_interval_end=interval_end) == "2026-10-02T14:10"
        with pytest.raises(AirflowFailException):
            minute_bucket_for(run_after=None, data_interval_end=None, logical_date=None)

    def test_bucket_passes_app_request_validation(self):
        # StartJobExecutionRequest: [A-Za-z0-9T:_-]+, 64자 이하
        bucket = minute_bucket_for(run_after=datetime(2026, 10, 2, 5, 7, tzinfo=timezone.utc))
        assert re.fullmatch(r"[A-Za-z0-9T:_-]+", bucket) and len(bucket) <= 64


def _shared_minute_cases():
    path = Path(__file__).resolve().parents[2] / "app/src/test/resources/batch/room-reconcile-minute-buckets.csv"
    with path.open() as source:
        return [(row["instant_utc"], row["bucket_kst"]) for row in csv.DictReader(source)]


@pytest.mark.parametrize("instant_utc,expected", _shared_minute_cases())
def test_shared_app_minute_bucket_contract(instant_utc, expected):
    assert minute_bucket_for(run_after=datetime.fromisoformat(instant_utc)) == expected


class TestBuildStartRequest:
    def test_url_headers_and_body(self):
        url, headers, body = build_start_request("http://host.docker.internal:8080/", "tok-" + "x" * 30, JOB_NAME, "2026-09-05")
        assert url == "http://host.docker.internal:8080/internal/batch/jobs/notificationCleanupJob/executions"
        assert headers["X-Internal-Token"].startswith("tok-")
        assert headers["Content-Type"] == "application/json"
        assert body == {"bucket": "2026-09-05"}

    def test_room_reconcile_job_url(self):
        url, _, body = build_start_request("http://app", "t" * 32, ROOM_RECONCILE_JOB_NAME, "2026-10-02T14:05")
        assert url == "http://app/internal/batch/jobs/roomReconcileJob/executions"
        assert body == {"bucket": "2026-10-02T14:05"}

    def test_blank_token_fails_fast(self):
        # 토큰 없이 부르면 app 이 어차피 401 이다. 재시도해도 소용없으니 즉시 실패(FailException)로 끝낸다.
        with pytest.raises(AirflowFailException):
            build_start_request("http://x", "", JOB_NAME, "2026-09-05")


class TestInterpretStartResponse:
    def _ok(self, outcome, execution_id=7, status="COMPLETED"):
        return {"isSuccess": True, "data": {"execution_id": execution_id, "job_name": JOB_NAME,
                                             "bucket": "2026-09-05", "status": status, "outcome": outcome}}

    def test_202_started_returns_execution_id(self):
        assert interpret_start_response(202, self._ok("STARTED")) == 7

    def test_200_already_completed_is_idempotent_success(self):
        assert interpret_start_response(200, self._ok("ALREADY_COMPLETED")) == 7

    def test_409_is_retryable(self):
        # 실행 중이거나 레이스 — 잠시 후 다시 부르면 된다. 재시도 가능한 AirflowException.
        body = {"isSuccess": False, "error": {"code": "BATCH_JOB_ALREADY_RUNNING", "message": "execution_id=5"}}
        with pytest.raises(AirflowException) as e:
            interpret_start_response(409, body)
        assert not isinstance(e.value, AirflowFailException)

    @pytest.mark.parametrize("code", [400, 401, 404])
    def test_client_errors_fail_without_retry(self, code):
        body = {"isSuccess": False, "error": {"code": "X", "message": "m"}}
        with pytest.raises(AirflowFailException):
            interpret_start_response(code, body)

    def test_malformed_success_body_is_retryable_not_fatal(self):
        # 서버가 잡을 시작한 뒤 응답이 잘렸을 수 있다 — 재호출(멱등)로 execution_id 를 복구해야 하므로 FailException 이면 안 된다.
        with pytest.raises(AirflowException) as e:
            interpret_start_response(202, {})
        assert not isinstance(e.value, AirflowFailException)

    def test_5xx_is_retryable(self):
        with pytest.raises(AirflowException) as e:
            interpret_start_response(503, {"isSuccess": False, "error": {"code": "DATABASE_ERROR", "message": "m"}})
        assert not isinstance(e.value, AirflowFailException)


class TestInterpretStatusResponse:
    def _body(self, status, exit_message=None):
        return {"isSuccess": True, "data": {"execution_id": 7, "status": status, "exit_code": status,
                                             "exit_message": exit_message, "steps": []}}

    def test_malformed_200_body_pokes_again(self):
        # 200 인데 data/status 가 없다 → 실패가 아니라 '아직 모름'
        assert interpret_status_response({}).is_done is False
        assert interpret_status_response({"isSuccess": True, "data": {}}).is_done is False

    def test_completed_is_done(self):
        assert interpret_status_response(self._body("COMPLETED")).is_done is True

    @pytest.mark.parametrize("status", ["STARTING", "STARTED", "STOPPING"])
    def test_running_states_keep_polling(self, status):
        assert interpret_status_response(self._body(status)).is_done is False

    @pytest.mark.parametrize("status", ["FAILED", "STOPPED", "ABANDONED", "UNKNOWN"])
    def test_terminal_failure_fails_immediately(self, status):
        # 잡이 이미 끝났는데 실패면 더 기다릴 이유가 없다 — 센서를 바로 실패시킨다(재시도 없음).
        with pytest.raises(AirflowFailException) as e:
            interpret_status_response(self._body(status, exit_message="boom"))
        assert status in str(e.value)


class TestClassifyStatusHttpFailure:
    @pytest.mark.parametrize("code", [400, 401, 403, 404])
    def test_permanent_client_errors_fail(self, code):
        assert classify_status_http_failure(code) == "fail"

    @pytest.mark.parametrize("code", [408, 429, 500, 502, 503, 504])
    def test_transient_errors_poke_again(self, code):
        assert classify_status_http_failure(code) == "poke_again"

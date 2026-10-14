"""haruharu_alerts 단위 테스트(#390). 알림 채널 분기(웹훅 유무)와 백필 데드라인 억제를 검증한다."""
import asyncio
import json

import pytest

import haruharu_alerts as alerts


class TestShouldAlertDeadline:
    def test_backfill_is_suppressed_by_default(self, monkeypatch):
        monkeypatch.delenv(alerts.DEADLINE_INCLUDE_BACKFILL_ENV, raising=False)
        assert alerts.should_alert_deadline({"run_type": "backfill"}) is False

    @pytest.mark.parametrize("run_type", ["scheduled", "manual", None])
    def test_other_run_types_alert(self, monkeypatch, run_type):
        monkeypatch.delenv(alerts.DEADLINE_INCLUDE_BACKFILL_ENV, raising=False)
        assert alerts.should_alert_deadline({"run_type": run_type}) is True

    def test_backfill_included_when_env_true(self, monkeypatch):
        monkeypatch.setenv(alerts.DEADLINE_INCLUDE_BACKFILL_ENV, "true")
        assert alerts.should_alert_deadline({"run_type": "backfill"}) is True

    def test_accepts_object_style_dag_run(self):
        class Run:  # 태스크 컨텍스트의 DagRun 객체 흉내
            run_type = "backfill"
        assert alerts.should_alert_deadline(Run(), include_backfill=False) is False


class TestNotify:
    def test_without_webhook_only_logs(self, monkeypatch, caplog):
        monkeypatch.delenv(alerts.SLACK_WEBHOOK_ENV, raising=False)
        calls = []
        monkeypatch.setattr("requests.post", lambda *a, **k: calls.append((a, k)))
        with caplog.at_level("INFO", logger="haruharu.alerts"):
            alerts.notify("TEST_EVENT", dag_id="d", run_id=None)
        assert calls == []
        structured = [r for r in caplog.records if r.levelname == "ERROR"]
        assert structured and json.loads(structured[0].getMessage())["event"] == "TEST_EVENT"
        assert any("webhook=skipped" in r.getMessage() for r in caplog.records)

    def test_with_webhook_posts_json(self, monkeypatch):
        monkeypatch.setenv(alerts.SLACK_WEBHOOK_ENV, "http://sink/hook")
        calls = []

        class Resp:
            def raise_for_status(self):
                return None

        monkeypatch.setattr("requests.post", lambda url, json=None, timeout=None: calls.append((url, json, timeout)) or Resp())
        alerts.notify("TEST_EVENT", dag_id="d")
        assert calls and calls[0][0] == "http://sink/hook"
        assert "TEST_EVENT" in calls[0][1]["text"]

    def test_payload_is_whitelisted_for_log_and_slack(self, monkeypatch, caplog):
        # 예외 원문 같은 자유 텍스트는 로그에도, Slack 에도 싣지 않는다 (상세는 Airflow 태스크 로그에 있다).
        monkeypatch.setenv(alerts.SLACK_WEBHOOK_ENV, "http://sink/hook")
        calls = []

        class Resp:
            def raise_for_status(self):
                return None

        monkeypatch.setattr("requests.post", lambda url, json=None, timeout=None: calls.append(json) or Resp())
        with caplog.at_level("ERROR", logger="haruharu.alerts"):
            alerts.notify("TEST_EVENT", dag_id="d", run_id="r", exception="Key (email)=(user@x.com) already exists")
        assert "user@x.com" not in calls[0]["text"]
        assert '"run_id": "r"' in calls[0]["text"]
        assert not any("user@x.com" in r.getMessage() for r in caplog.records if r.levelname == "ERROR")

    def test_webhook_failure_does_not_raise_and_hides_url(self, monkeypatch, caplog):
        monkeypatch.setenv(alerts.SLACK_WEBHOOK_ENV, "http://sink/hook/SECRET-PART")

        def boom(*a, **k):
            raise RuntimeError("POST http://sink/hook/SECRET-PART failed")

        monkeypatch.setattr("requests.post", boom)
        with caplog.at_level("WARNING", logger="haruharu.alerts"):
            alerts.notify("TEST_EVENT")  # 알림 실패가 원래 실패를 덮으면 안 된다
        assert not any("SECRET-PART" in r.getMessage() for r in caplog.records)  # 웹훅 URL 은 자격증명 — 로그에 안 남긴다


class TestWebhookTarget:
    """#411: 웹훅 URL 로 Slack/Discord 를 판별해 본문 형식을 맞춘다. 환경변수 이름은 그대로(값만 바뀜)."""

    class _Resp:
        def raise_for_status(self):
            return None

    def _capture(self, monkeypatch, url):
        monkeypatch.setenv(alerts.SLACK_WEBHOOK_ENV, url)
        calls = []
        monkeypatch.setattr("requests.post", lambda u, json=None, timeout=None: calls.append((u, json)) or self._Resp())
        return calls

    def test_discord_url_uses_content_key(self, monkeypatch):
        calls = self._capture(monkeypatch, "https://discord.com/api/webhooks/123/abc")
        alerts.notify("TEST_EVENT", dag_id="d")
        assert calls and "content" in calls[0][1] and "text" not in calls[0][1]
        assert "TEST_EVENT" in calls[0][1]["content"]

    def test_discordapp_legacy_domain_also_discord(self, monkeypatch):
        calls = self._capture(monkeypatch, "https://discordapp.com/api/webhooks/123/abc")
        alerts.notify("TEST_EVENT")
        assert "content" in calls[0][1]

    def test_slack_url_keeps_text_key(self, monkeypatch):
        calls = self._capture(monkeypatch, "https://hooks.slack.com/services/T/B/x")
        alerts.notify("TEST_EVENT")
        assert "text" in calls[0][1] and "content" not in calls[0][1]

    def test_discord_message_truncated_to_2000_with_closed_fence(self, monkeypatch):
        calls = self._capture(monkeypatch, "https://discord.com/api/webhooks/123/abc")
        alerts.notify("TEST_EVENT", run_id="x" * 3000)
        content = calls[0][1]["content"]
        assert len(content) <= 2000
        assert content.endswith("```") and "(truncated)" in content   # 펜스가 닫혀 렌더링이 안 깨진다
        assert content.count("```") % 2 == 0

    @pytest.mark.parametrize("url,kind", [
        ("https://discord.com/api/webhooks/123/abc", "discord"),
        ("https://DISCORD.COM/api/webhooks/123/abc", "discord"),            # 대문자 호스트
        ("https://discordapp.com/api/webhooks/123/abc/", "discord"),        # 구 도메인·끝 슬래시
        ("https://ptb.discord.com/api/webhooks/123/abc?wait=true", "discord"),  # 서브도메인·쿼리
        ("https://discord.com/api/webhooks/123/abc/slack", "slack"),        # Discord 의 Slack 호환 엔드포인트
        ("https://discord.com/api/webhooks/123/abc/github", "slack"),       # GitHub 호환(지원 안 함 → 기본 형식)
        ("https://notdiscord.com/api/webhooks/123/abc", "slack"),           # 유사 도메인 오탐 방지
        ("https://example.org/hook?x=discord.com/api/webhooks", "slack"),   # 쿼리스트링 오탐 방지
        ("https://hooks.slack.com/services/T/B/x", "slack"),
        ("not a url", "slack"),
    ])
    def test_webhook_kind_by_hostname(self, url, kind):
        assert alerts.webhook_kind(url) == kind

    def test_webhook_kind_logged_not_url(self, monkeypatch, caplog):
        self._capture(monkeypatch, "https://discord.com/api/webhooks/123/SECRET")
        with caplog.at_level("INFO", logger="haruharu.alerts"):
            alerts.notify("TEST_EVENT")
        joined = " ".join(r.getMessage() for r in caplog.records)
        assert "discord" in joined.lower() and "SECRET" not in joined


class TestDeadlineMissedCallback:
    def _ctx(self, run_type):
        return {"context": {"dag_run": {"dag_run_id": "r1", "dag_id": "notification_cleanup", "run_type": run_type,
                                        "state": "running"},
                            "deadline": {"deadline_time": "2026-09-04T19:15:00+00:00"}},
                "dag_id": "notification_cleanup", "deadline_minutes": 15}

    def test_scheduled_run_notifies_with_run_id(self, monkeypatch, caplog):
        monkeypatch.delenv(alerts.SLACK_WEBHOOK_ENV, raising=False)
        monkeypatch.delenv(alerts.DEADLINE_INCLUDE_BACKFILL_ENV, raising=False)
        with caplog.at_level("ERROR", logger="haruharu.alerts"):
            asyncio.run(alerts.deadline_missed(**self._ctx("scheduled")))
        msg = json.loads([r for r in caplog.records if r.levelname == "ERROR"][0].getMessage())
        assert msg["event"] == "NOTIFICATION_CLEANUP_DEADLINE_MISSED" and msg["run_id"] == "r1"

    def test_backfill_run_is_silent(self, monkeypatch, caplog):
        monkeypatch.delenv(alerts.DEADLINE_INCLUDE_BACKFILL_ENV, raising=False)
        with caplog.at_level("ERROR", logger="haruharu.alerts"):
            asyncio.run(alerts.deadline_missed(**self._ctx("backfill")))
        assert not [r for r in caplog.records if r.levelname == "ERROR"]


@pytest.fixture
def default_alert_period(monkeypatch):
    # 컨테이너에 검증용 주기(예: 10분)가 설정돼 있어도 기본값(30분) 기준으로 검증한다
    monkeypatch.delenv(alerts.RECONCILE_ALERT_PERIOD_ENV, raising=False)


def _t(hour, minute, second=0):
    from datetime import datetime, timezone
    return datetime(2026, 10, 2, hour, minute, second, tzinfo=timezone.utc)


def _alerted_runs(runs, prev_success, period=None):
    """연속 실패 run 들을 순서대로 판정한다. 각 run 의 '직전 run' 은 목록의 앞 원소(첫 run 은 마지막 성공 run)다."""
    alerted, previous = [], prev_success
    for run in runs:
        if alerts.should_alert_outage(run, prev_success, previous, period=period):
            alerted.append(run)
        previous = run
    return alerted


@pytest.mark.usefixtures("default_alert_period")
class TestShouldAlertOutage:
    """room_reconcile(#469) 실패 알림 억제. 매분 도는 DAG에서 run마다 알리면 장애 1시간에 60건이 간다.

    규칙: outage = 이번 run logical_date − 마지막 성공 run 시작 시각. outage 가 period(기본 30분)의 새 배수를 **직전 run 이후 처음
    넘은 run** 에서만 알린다(floor(outage/period) 가 직전 run 보다 커졌을 때). 좁은 시간 창이 아니라 경계 통과로 판단하므로
    run 이 밀리거나 건너뛰어져도(catchup=False) 장애가 이어지면 주기마다 반드시 한 번 알린다.
    """

    def test_short_outage_is_suppressed(self):
        # 마지막 성공 10:00:03 시작, 10:05~10:25 실패 — 아직 알리지 않는다(앱 cron 폴백이 같은 bucket 을 처리한다)
        assert _alerted_runs([_t(10, m) for m in range(5, 30, 5)], _t(10, 0, 3)) == []

    def test_alerts_once_per_period_during_sustained_outage(self):
        runs = [_t(10, m) for m in range(5, 60, 5)] + [_t(11, m) for m in range(0, 60, 5)]
        # 경과 34분57초·64분57초·94분57초 run — 30분마다 정확히 1건
        assert _alerted_runs(runs, _t(10, 0, 3)) == [_t(10, 35), _t(11, 5), _t(11, 35)]

    def test_minute_schedule_still_alerts_only_once_per_thirty_minutes(self):
        runs = [_t(10, m) for m in range(1, 60)] + [_t(11, m) for m in range(60)]
        assert _alerted_runs(runs, _t(10, 0, 3)) == [_t(10, 31), _t(11, 1), _t(11, 31)]

    def test_exact_boundary_alerts_once(self):
        assert _alerted_runs([_t(10, m) for m in range(5, 60, 5)], _t(10, 0, 0)) == [_t(10, 30)]

    def test_delayed_runs_still_alert_every_period(self):
        # Codex 리뷰 재현: run 이 10분 간격으로 밀리면 좁은 창(outage % period < 5분)은 3시간 내내 0건이었다.
        runs = [_t(h, m) for h in (10, 11, 12) for m in range(0, 60, 10) if (h, m) != (10, 0)] + [_t(13, 0)]
        alerted = _alerted_runs(runs, _t(10, 0, 3))
        assert alerted == [_t(10, 40), _t(11, 10), _t(11, 40), _t(12, 10), _t(12, 40)]

    def test_irregular_gaps_alert_on_first_run_past_each_boundary(self):
        # 10:05 → (긴 지연) → 10:47 → 11:31: 경계(30·60·90분)를 넘은 첫 run 마다 알린다
        assert _alerted_runs([_t(10, 5), _t(10, 47), _t(11, 31)], _t(10, 0, 0)) == [_t(10, 47), _t(11, 31)]

    def test_skipping_several_periods_alerts_once(self):
        # 직전 run 10:20(경과 20분) 다음 run 이 11:50(110분) — 경계 셋을 한 번에 넘어도 1건
        assert _alerted_runs([_t(10, 20), _t(11, 50)], _t(10, 0, 0)) == [_t(11, 50)]

    def test_first_failure_after_long_quiet_success_gap_alerts(self):
        # 직전 run 이 마지막 성공 run 자신인데 이미 period 를 넘었다(스케줄러 정지 후 재개 등) — 바로 알린다
        assert alerts.should_alert_outage(_t(11, 0), _t(10, 0, 0), _t(10, 0, 0)) is True

    def test_pendulum_datetimes_from_airflow_context(self):
        # Airflow 컨텍스트의 logical_date·prev_start_date_success 는 pendulum DateTime 이다. 둘을 빼면 pendulum Interval 이
        # 나오는데 Interval % timedelta 는 AttributeError 다(실제 연동 검증에서 장애 30분 시점 알림이 폴백 경로로 샌 원인).
        import pendulum
        prev_success = pendulum.datetime(2026, 10, 2, 10, 0, 3, tz="UTC")
        runs = [pendulum.datetime(2026, 10, 2, 10, m, tz="UTC") for m in range(5, 60, 5)]
        assert _alerted_runs(runs, prev_success) == [pendulum.datetime(2026, 10, 2, 10, 35, tz="UTC")]

    def test_run_without_logical_date_always_alerts(self):
        assert alerts.should_alert_outage(None, _t(10, 0), _t(10, 0)) is True

    def test_no_previous_success_alerts_on_first_failure_then_each_wall_clock_period(self):
        # 기준 성공이 없으면(첫 배포 직후 설정 오류 등) 첫 실패 run 에서 알리고, 이후 벽시계 period 경계를 넘을 때마다 한 번
        assert alerts.should_alert_outage(_t(10, 5), None, None) is True
        alerted, previous = [], _t(10, 5)
        for run in [_t(10, m) for m in range(10, 60, 5)]:
            if alerts.should_alert_outage(run, None, previous):
                alerted.append(run)
            previous = run
        assert alerted == [_t(10, 30)]

    def test_period_from_env(self, monkeypatch):
        monkeypatch.setenv(alerts.RECONCILE_ALERT_PERIOD_ENV, "10")
        runs = [_t(10, m) for m in range(5, 35, 5)]
        assert _alerted_runs(runs, _t(10, 0, 0)) == [_t(10, 10), _t(10, 20), _t(10, 30)]


@pytest.mark.usefixtures("default_alert_period")
class TestRoomReconcileTaskFailed:
    def _context(self, logical_date, prev_success, prev_run_logical_date=None, run_type="scheduled",
                 prev_run_error=None):
        class Run:
            dag_id = "room_reconcile"
            run_id = "scheduled__2026-10-02T10:35:00+00:00"

        Run.run_type = run_type

        class PreviousRun:
            logical_date = prev_run_logical_date

        class Ti:
            task_id = "start_job"

            def get_previous_dagrun(self, state=None):
                if prev_run_error:
                    raise prev_run_error
                return PreviousRun() if prev_run_logical_date else None

        return {"dag_run": Run(), "task_instance": Ti(), "logical_date": logical_date,
                "prev_start_date_success": prev_success}

    @pytest.fixture
    def sent(self, monkeypatch):
        captured = []
        monkeypatch.setattr(alerts, "notify", lambda event, **f: captured.append((event, f)))
        return captured

    def test_suppressed_failure_does_not_notify(self, sent):
        alerts.room_reconcile_task_failed(self._context(_t(10, 25), _t(10, 0, 3), _t(10, 20)))
        assert sent == []

    def test_first_run_past_boundary_notifies_with_outage_minutes(self, sent):
        alerts.room_reconcile_task_failed(self._context(_t(10, 35), _t(10, 0, 3), _t(10, 30)))
        assert len(sent) == 1
        event, fields = sent[0]
        assert event == "ROOM_RECONCILE_FAILED"
        assert fields["dag_id"] == "room_reconcile" and fields["failed_task"] == "start_job"
        assert fields["outage_minutes"] == 34
        assert "outage_minutes" in alerts.ALERT_FIELDS  # 화이트리스트에 없으면 notify 가 버린다

    def test_manual_run_with_logical_date_always_notifies(self, sent):
        # Codex 리뷰 재현: API 로 logical_date 를 지정한 수동 run 은 logical_date 가 있어 억제 규칙을 탔다
        alerts.room_reconcile_task_failed(self._context(_t(10, 10), _t(10, 0, 0), _t(10, 5), run_type="manual"))
        assert len(sent) == 1

    def test_manual_run_type_enum_value_is_recognized(self, sent):
        from airflow.utils.types import DagRunType
        alerts.room_reconcile_task_failed(self._context(_t(10, 10), _t(10, 0, 0), _t(10, 5), run_type=DagRunType.MANUAL))
        assert len(sent) == 1

    def test_lazy_proxy_wrapping_none_is_treated_as_no_previous_success(self, sent):
        # Airflow 는 prev_start_date_success 를 lazy_object_proxy.Proxy 로 준다 — None 을 감싼 Proxy 는 `is None` 이 False 다
        import lazy_object_proxy
        alerts.room_reconcile_task_failed(self._context(_t(10, 30), lazy_object_proxy.Proxy(lambda: None)))
        assert len(sent) == 1 and sent[0][1]["outage_minutes"] is None

    def test_failure_to_read_previous_success_alerts(self, sent):
        # 이전 성공 조회 자체가 실패하면 억제 근거가 없다 — 조용히 삼키지 않고 알린다
        import lazy_object_proxy

        def boom():
            raise RuntimeError("supervisor comms down")

        alerts.room_reconcile_task_failed(self._context(_t(10, 25), lazy_object_proxy.Proxy(boom), _t(10, 20)))
        assert len(sent) == 1

    def test_failure_to_read_previous_run_alerts(self, sent):
        alerts.room_reconcile_task_failed(
            self._context(_t(10, 25), _t(10, 0, 3), _t(10, 20), prev_run_error=RuntimeError("comms down")))
        assert len(sent) == 1

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

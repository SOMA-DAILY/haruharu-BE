"""알림 채널(#390). 실패·데드라인 초과를 구조화 로그로 남기고, Slack Webhook URL 이 설정돼 있으면 Slack 에도 보낸다.

Airflow 는 '언제 알릴지'(on_failure_callback, DeadlineAlert)를 제공하고, '어디로'만 여기서 정한다.
Airflow 자신이 죽은 경우는 이 코드로 감지할 수 없다 — 그건 외부 헬스체크(인프라 이슈) 몫.
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
from typing import Any

log = logging.getLogger("haruharu.alerts")

def _field(obj: Any, *names: str) -> Any:
    """dag_run 이 객체(태스크 컨텍스트)일 수도, dict(데드라인 콜백 컨텍스트)일 수도 있어 둘 다 읽는다."""
    for name in names:
        if isinstance(obj, dict) and obj.get(name) is not None:
            return obj[name]
        if hasattr(obj, name) and getattr(obj, name) is not None:
            return getattr(obj, name)
    return None


SLACK_WEBHOOK_ENV = "HARUHARU_ALERT_SLACK_WEBHOOK_URL"
# 알림(로그·Slack)에 싣는 필드 화이트리스트. 예외 원문·exit_message 같은 자유 텍스트는 아예 싣지 않는다 —
# 상세는 Airflow 태스크 로그에 이미 있고, 여기 실으면 PII·내부정보가 구조화 로그와 외부 SaaS 로 복제된다.
ALERT_FIELDS = ("event", "dag_id", "run_id", "run_state", "failed_task", "deadline_time", "deadline_minutes")
DEADLINE_INCLUDE_BACKFILL_ENV = "HARUHARU_ALERT_DEADLINE_INCLUDE_BACKFILL"


def should_alert_deadline(dag_run: Any, include_backfill: bool | None = None) -> bool:
    """백필 실행은 기본적으로 데드라인 알림에서 제외한다.

    데드라인 = logical_date + N분 인데 백필은 과거 날짜를 지금 돌리므로 생성 즉시 '초과'가 된다 — 운영 이상이 아니라
    구조적 결과다. 감시 대상은 스케줄 실행(04:00 KST)이다. 로컬 실험용으로 환경변수로 포함시킬 수 있다.
    """
    if include_backfill is None:
        include_backfill = os.environ.get(DEADLINE_INCLUDE_BACKFILL_ENV, "false").strip().lower() == "true"
    run_type = _field(dag_run, "run_type")
    return include_backfill or run_type != "backfill"


def _post_slack(text: str) -> bool:
    url = os.environ.get(SLACK_WEBHOOK_ENV, "").strip()
    if not url:
        return False
    import requests  # 콜백은 triggerer/worker 어디서든 돌 수 있어 지연 import

    try:
        requests.post(url, json={"text": text}, timeout=10).raise_for_status()
        return True
    except Exception as e:  # noqa: BLE001 — 알림 실패가 원래 실패를 가리면 안 된다
        # 예외 메시지에 웹훅 URL(그 자체가 자격증명)이 실릴 수 있어 타입만 남긴다.
        log.warning("Slack 알림 전송 실패: %s", type(e).__name__)
        return False


def notify(event: str, **fields: Any) -> None:
    """구조화 로그 1줄(항상) + Slack(선택). ALERT_FIELDS 밖의 필드는 버린다(로그에도 안 남긴다).

    로그는 Datadog 등에서 event 키로 필터할 수 있게 JSON 한 줄이다.
    """
    dropped = sorted(k for k in fields if k not in ALERT_FIELDS)
    payload = {"event": event, **{k: (str(v) if v is not None else None) for k, v in fields.items() if k in ALERT_FIELDS}}
    line = json.dumps(payload, ensure_ascii=False)
    log.error(line)
    if dropped:
        log.debug("알림에서 제외한 필드(화이트리스트 밖): %s", dropped)
    sent = _post_slack(f"[haruharu-airflow] {event}\n```{line}```")
    log.info("알림 채널: slack=%s", "sent" if sent else "skipped(no webhook)")


def dag_failed(context: dict[str, Any]) -> None:
    """DAG 레벨 on_failure_callback — 재시도를 다 소진하고 DAG Run 이 실패로 확정될 때 1회 (dag-processor 에서 실행)."""
    dag_run = context.get("dag_run")
    ti = context.get("task_instance")
    # 예외 원문은 싣지 않는다 — 태스크 로그에 스택트레이스가 이미 있다. 여기선 "어느 run 의 어느 태스크" 만.
    notify(
        "NOTIFICATION_CLEANUP_DAG_FAILED",
        dag_id=_field(dag_run, "dag_id"),
        run_id=_field(dag_run, "run_id", "dag_run_id"),
        failed_task=_field(ti, "task_id"),
    )


async def deadline_missed(**kwargs: Any) -> None:
    """DeadlineAlert AsyncCallback — triggerer 에서 실행된다. 데드라인 시각이 지났는데 DAG Run 이 안 끝난 경우.

    kwargs["context"]["dag_run"] 은 dict 다(Airflow 가 직렬화해 넘긴다). deadline_time 도 함께 넘어온다.
    """
    context = kwargs.get("context") or {}
    dag_run = context.get("dag_run") if isinstance(context, dict) else None
    deadline = context.get("deadline") if isinstance(context, dict) else None
    if not should_alert_deadline(dag_run):
        log.info("백필 실행의 데드라인 초과는 알리지 않는다: run_id=%s", _field(dag_run, "dag_run_id", "run_id"))
        return
    # triggerer 는 단일 이벤트 루프다. 동기 HTTP(requests)로 루프를 막지 않도록 스레드로 내린다.
    await asyncio.to_thread(
        notify,
        "NOTIFICATION_CLEANUP_DEADLINE_MISSED",
        dag_id=kwargs.get("dag_id") or _field(dag_run, "dag_id"),
        run_id=_field(dag_run, "dag_run_id", "run_id"),
        run_state=_field(dag_run, "state"),
        deadline_time=_field(deadline, "deadline_time"),
        deadline_minutes=kwargs.get("deadline_minutes"),
    )

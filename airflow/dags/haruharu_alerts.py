"""알림 채널(#390, #411, #469). 실패·데드라인 초과를 구조화 로그로 남기고, 웹훅 URL 이 설정돼 있으면 Slack 또는 Discord 로도 보낸다.

Airflow 는 '언제 알릴지'(on_failure_callback, DeadlineAlert)를 제공하고, '어디로'만 여기서 정한다.
Airflow 자신이 죽은 경우는 이 코드로 감지할 수 없다 — 그건 외부 헬스체크(인프라 이슈) 몫.
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
from datetime import datetime, timedelta
from urllib.parse import urlsplit
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


# 이름은 역사적으로 SLACK 이지만 값은 Slack 또는 Discord Incoming Webhook URL 어느 쪽이든 된다(#411).
# 시크릿 키·태스크 정의·compose 를 안 건드리려고 이름은 유지하고, URL 로 대상을 판별한다.
SLACK_WEBHOOK_ENV = "HARUHARU_ALERT_SLACK_WEBHOOK_URL"
DISCORD_HOSTS = ("discord.com", "discordapp.com")   # 서브도메인(ptb./canary.) 포함 — 접미사로 비교
DISCORD_MAX_CONTENT = 2000  # Discord 메시지 본문 상한
_TRUNCATED_TAIL = "\n…(truncated)```"
# 알림(로그·Slack)에 싣는 필드 화이트리스트. 예외 원문·exit_message 같은 자유 텍스트는 아예 싣지 않는다 —
# 상세는 Airflow 태스크 로그에 이미 있고, 여기 실으면 PII·내부정보가 구조화 로그와 외부 SaaS 로 복제된다.
ALERT_FIELDS = ("event", "dag_id", "run_id", "run_state", "failed_task", "deadline_time", "deadline_minutes",
                "outage_minutes")
DEADLINE_INCLUDE_BACKFILL_ENV = "HARUHARU_ALERT_DEADLINE_INCLUDE_BACKFILL"
# room_reconcile(#469) 실패 알림 주기(분). '마지막 성공 이후 이만큼' 부터, 이 간격마다 한 번 알린다. 로컬 실험 시 줄인다.
RECONCILE_ALERT_PERIOD_ENV = "HARUHARU_RECONCILE_ALERT_PERIOD_MINUTES"


def should_alert_deadline(dag_run: Any, include_backfill: bool | None = None) -> bool:
    """백필 실행은 기본적으로 데드라인 알림에서 제외한다.

    데드라인 = logical_date + N분 인데 백필은 과거 날짜를 지금 돌리므로 생성 즉시 '초과'가 된다 — 운영 이상이 아니라
    구조적 결과다. 감시 대상은 스케줄 실행(04:00 KST)이다. 로컬 실험용으로 환경변수로 포함시킬 수 있다.
    """
    if include_backfill is None:
        include_backfill = os.environ.get(DEADLINE_INCLUDE_BACKFILL_ENV, "false").strip().lower() == "true"
    run_type = _field(dag_run, "run_type")
    return include_backfill or run_type != "backfill"


def webhook_kind(url: str) -> str:
    """'discord' | 'slack'.

    호스트명을 파싱해 판별한다(부분 문자열 매칭은 `notdiscord.com`·쿼리스트링·대문자 호스트를 오판한다).
    Discord 네이티브 웹훅(`/api/webhooks/{id}/{token}`)만 'discord'. Discord 의 Slack 호환 엔드포인트(`.../slack`)는
    Slack 형식(text)을 받으므로 'slack' 으로 둔다. 그 외(hooks.slack.com 등)는 전부 Slack 형식.
    """
    try:
        parts = urlsplit(url.strip())
    except ValueError:
        return "slack"
    host = (parts.hostname or "").lower()
    path = parts.path.rstrip("/")
    is_discord_host = any(host == h or host.endswith("." + h) for h in DISCORD_HOSTS)
    if is_discord_host and "/api/webhooks/" in path and not path.endswith(("/slack", "/github")):
        return "discord"
    return "slack"


def _webhook_body(kind: str, text: str) -> dict[str, str]:
    if kind == "discord":
        return {"content": _truncate_keeping_fence(text, DISCORD_MAX_CONTENT)}
    return {"text": text}


def _truncate_keeping_fence(text: str, limit: int) -> str:
    """상한을 넘으면 잘라내되, 잘렸음을 표시하고 코드 펜스(```)를 닫아 Discord 렌더링이 깨지지 않게 한다."""
    if len(text) <= limit:
        return text
    return text[: limit - len(_TRUNCATED_TAIL)] + _TRUNCATED_TAIL


def _post_webhook(text: str) -> str | None:
    """전송했으면 채널 종류('discord'/'slack'), 미설정·실패면 None. URL 은 절대 로그에 남기지 않는다."""
    url = os.environ.get(SLACK_WEBHOOK_ENV, "").strip()
    if not url:
        return None
    import requests  # 콜백은 triggerer/worker 어디서든 돌 수 있어 지연 import

    kind = webhook_kind(url)
    try:
        requests.post(url, json=_webhook_body(kind, text), timeout=10).raise_for_status()
        return kind
    except Exception as e:  # noqa: BLE001 — 알림 실패가 원래 실패를 가리면 안 된다
        # 예외 메시지에 웹훅 URL(그 자체가 자격증명)이 실릴 수 있어 타입만 남긴다.
        log.warning("%s 알림 전송 실패: %s", kind, type(e).__name__)
        return None


def notify(event: str, **fields: Any) -> None:
    """구조화 로그 1줄(항상) + 웹훅(선택, Slack/Discord). ALERT_FIELDS 밖의 필드는 버린다(로그에도 안 남긴다).

    로그는 Datadog 등에서 event 키로 필터할 수 있게 JSON 한 줄이다.
    """
    dropped = sorted(k for k in fields if k not in ALERT_FIELDS)
    payload = {"event": event, **{k: (str(v) if v is not None else None) for k, v in fields.items() if k in ALERT_FIELDS}}
    line = json.dumps(payload, ensure_ascii=False)
    log.error(line)
    if dropped:
        log.debug("알림에서 제외한 필드(화이트리스트 밖): %s", dropped)
    sent = _post_webhook(f"[haruharu-airflow] {event}\n```{line}```")
    log.info("알림 채널: webhook=%s", sent or "skipped(no webhook)")


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


def _reconcile_alert_period() -> timedelta:
    return timedelta(minutes=int(os.environ.get(RECONCILE_ALERT_PERIOD_ENV, "30")))


def should_alert_outage(logical_date: datetime | None, prev_success_start: datetime | None,
                        prev_run_logical_date: datetime | None, period: timedelta | None = None) -> bool:
    """5분 주기 DAG 의 실패 알림을 '연속 실패가 period 이상 이어질 때, period 마다 한 번' 으로 줄인다(#469).

    상태(카운터·변수)를 저장하지 않는다 — Airflow 가 주는 '마지막 성공 run 의 시작 시각' 과 '직전 run 의 logical_date' 로 판단한다.
      outage = 이번 run logical_date − 마지막 성공 run 시작 시각
    floor(outage / period) 가 **직전 run 보다 커진 run**(= period 의 새 배수를 처음 넘은 run)에서만 알린다 → 장애 30·60·90분… 에 한 건씩.
    좁은 시간 창(outage % period < 5분)으로 판단하면 run 이 밀리거나 건너뛰어질 때(catchup=False·max_active_runs=1) 창을 계속 놓쳐
    지속 장애를 영영 못 알릴 수 있다(Codex 리뷰). 경계 통과로 판단하면 run 간격과 무관하게 주기마다 반드시 한 번 알린다.
    30분 안에 회복한 일시 장애는 알리지 않는다(그동안 같은 bucket 은 앱 cron 폴백이 처리한다).

    - logical_date 없음: 기준을 잡을 수 없으니 바로 알린다(수동 run 판별은 호출자가 run_type 으로 먼저 한다).
    - 이전 성공 없음(첫 배포 직후 설정 오류 등): 첫 실패에서 알리고, 이후 벽시계 period 경계를 넘을 때마다 한 번.
    - 직전 run 이 마지막 성공 run 자신(또는 없음)인데 이미 period 를 넘었다(스케줄러 정지 후 재개 등): 바로 알린다.
    """
    if logical_date is None:
        return True
    period = period or _reconcile_alert_period()
    if prev_success_start is None:
        if prev_run_logical_date is None:
            return True
        return _wall_clock_periods(logical_date, period) > _wall_clock_periods(prev_run_logical_date, period)
    periods_now = _elapsed_periods(logical_date - prev_success_start, period)
    if periods_now < 1:
        return False
    if prev_run_logical_date is None or prev_run_logical_date <= prev_success_start:
        return True
    return periods_now > _elapsed_periods(prev_run_logical_date - prev_success_start, period)


def _elapsed_periods(elapsed: timedelta, period: timedelta) -> int:
    # Airflow 가 주는 값은 pendulum DateTime 이라 빼면 pendulum Interval 이 된다. Interval 과 timedelta 의 % · // 연산은
    # AttributeError 가 나므로(실제 연동 검증에서 확인) 초 단위 실수로만 계산한다.
    return int(elapsed.total_seconds() // period.total_seconds())


def _wall_clock_periods(moment: datetime, period: timedelta) -> int:
    return int(moment.timestamp() // period.total_seconds())


def _is_manual(dag_run: Any) -> bool:
    run_type = _field(dag_run, "run_type")
    return str(getattr(run_type, "value", run_type)) == "manual"


def _resolved_datetime(value: Any) -> datetime | None:
    """Airflow 는 prev_start_date_success 를 lazy_object_proxy.Proxy 로 준다. None 을 감싼 Proxy 는 `is None` 이 False 라
    isinstance 로 실제 값을 가린다(Proxy 는 __class__ 를 대상 타입으로 위장한다). 조회 실패 예외는 호출자에게 그대로 올린다."""
    return value if isinstance(value, datetime) else None


def room_reconcile_task_failed(context: dict[str, Any]) -> None:
    """room_reconcile 태스크 on_failure_callback — 재시도를 다 소진하고 태스크가 실패로 확정될 때(태스크를 실행한 프로세스에서).

    run 마다 부르지만 웹훅은 수동 run 이거나 should_alert_outage 를 통과한 run 만 보낸다. 억제한 실패도 태스크 로그에는 남긴다.
    """
    dag_run = context.get("dag_run")
    ti = context.get("task_instance")
    logical_date = _resolved_datetime(context.get("logical_date") or _field(dag_run, "logical_date"))
    # 사람이 직접 돌린 run 은 바로 알린다. API 로 logical_date 를 지정한 수동 run 도 있어 logical_date 유무로는 판별할 수 없다.
    manual = _is_manual(dag_run)
    prev_success_start = None
    try:
        prev_success_start = _resolved_datetime(context.get("prev_start_date_success"))
        prev_run = ti.get_previous_dagrun() if ti is not None and logical_date is not None else None
        prev_run_logical_date = _resolved_datetime(_field(prev_run, "logical_date"))
        alert = manual or should_alert_outage(logical_date, prev_success_start, prev_run_logical_date)
    except Exception as e:  # noqa: BLE001 — 억제 근거를 못 구하면 조용히 삼키지 않고 알린다
        log.warning("이전 run 조회 실패 — 억제하지 않고 알린다: %s", type(e).__name__)
        prev_success_start, alert = None, True
    outage_minutes = (int((logical_date - prev_success_start).total_seconds() // 60)
                      if logical_date and prev_success_start else None)
    if not alert:
        log.info("room_reconcile 실패 알림 억제: outage_minutes=%s run_id=%s", outage_minutes,
                 _field(dag_run, "run_id", "dag_run_id"))
        return
    notify(
        "ROOM_RECONCILE_FAILED",
        dag_id=_field(dag_run, "dag_id"),
        run_id=_field(dag_run, "run_id", "dag_run_id"),
        failed_task=_field(ti, "task_id"),
        outage_minutes=outage_minutes,
    )

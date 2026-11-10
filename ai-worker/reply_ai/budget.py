"""비용 한도. 모델 단계 전에 최대 비용을 원자적으로 예약하고, 끝나면 실제 사용으로 옮긴다.

온라인 생성은 KST 하루 단위(budget_day, scope=online), 피드백 배치는 실행 단위(batch_run)다.
예약 뒤 프로세스가 죽으면 예약이 남는다(보수적으로 한도를 더 일찍 막는 쪽).
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Any

from . import errors
from .config import Settings
from .util import kst_day


@dataclass
class Reservation:
    kind: str            # online | batch
    key: Any             # day(date) | batch_run_id
    amount: float
    settled: bool = False


def estimate_usd(settings: Settings, input_tokens: int, max_output_tokens: int) -> float:
    return (input_tokens * settings.price_input_per_m + max_output_tokens * settings.price_output_per_m) / 1_000_000


def reserve_online(conn, settings: Settings, amount: float, day: date | None = None) -> Reservation:
    day = day or kst_day()
    with conn.transaction():
        conn.execute(
            "INSERT INTO ai_reply.budget_day (day, scope, limit_usd) VALUES (%s, 'online', %s) ON CONFLICT DO NOTHING",
            (day, settings.daily_budget_usd))
        row = conn.execute(
            """UPDATE ai_reply.budget_day
                  SET limit_usd = %(limit)s, reserved_usd = reserved_usd + %(amount)s, updated_at = now()
                WHERE day = %(day)s AND scope = 'online'
                  AND reserved_usd + spent_usd + %(amount)s <= %(limit)s
            RETURNING day""",
            {"limit": settings.daily_budget_usd, "amount": amount, "day": day}).fetchone()
    if row is None:
        raise errors.JobFailure(errors.BUDGET_EXCEEDED, f"online {day}")
    return Reservation("online", day, amount)


def reserve_batch(conn, batch_run_id: int, amount: float) -> Reservation:
    row = conn.execute(
        """UPDATE ai_reply.batch_run SET reserved_usd = reserved_usd + %(amount)s, updated_at = now()
            WHERE id = %(id)s AND reserved_usd + spent_usd + %(amount)s <= budget_usd RETURNING id""",
        {"amount": amount, "id": batch_run_id}).fetchone()
    if row is None:
        raise errors.JobFailure(errors.BUDGET_EXCEEDED, f"batch {batch_run_id}")
    return Reservation("batch", batch_run_id, amount)


def settle(conn, reservation: Reservation, actual_usd: float) -> None:
    """예약을 풀고 실제 비용을 더한다. 실패한 호출은 actual_usd=0 으로 풀기만 한다."""
    if reservation.settled:
        return
    if reservation.kind == "online":
        conn.execute(
            """UPDATE ai_reply.budget_day
                  SET reserved_usd = greatest(reserved_usd - %(r)s, 0), spent_usd = spent_usd + %(a)s, updated_at = now()
                WHERE day = %(day)s AND scope = 'online'""",
            {"r": reservation.amount, "a": actual_usd, "day": reservation.key})
    else:
        conn.execute(
            """UPDATE ai_reply.batch_run
                  SET reserved_usd = greatest(reserved_usd - %(r)s, 0), spent_usd = spent_usd + %(a)s, updated_at = now()
                WHERE id = %(id)s""",
            {"r": reservation.amount, "a": actual_usd, "id": reservation.key})
    reservation.settled = True

"""해시·시각·정렬 JSON 같은 작은 공통 도구."""
from __future__ import annotations

import hashlib
import json
from datetime import datetime, timedelta, timezone
from typing import Any
from zoneinfo import ZoneInfo

KST = ZoneInfo("Asia/Seoul")


def canonical(value: Any) -> str:
    """키를 정렬한 한 줄 JSON. 해시·멱등 키는 모두 이 형식으로 만든다."""
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=_default)


def _default(value: Any) -> Any:
    if isinstance(value, datetime):
        return iso(value)
    raise TypeError(f"not JSON serializable: {type(value).__name__}")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_text(text: str) -> str:
    return sha256_bytes(text.encode("utf-8"))


def sha256_json(value: Any) -> str:
    return sha256_text(canonical(value))


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def iso(value: datetime) -> str:
    """UTC ISO-8601 (Z). 시간대 없는 값은 받지 않는다."""
    if value.tzinfo is None:
        raise ValueError("timezone-aware datetime required")
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def parse_iso(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("timestamp needs an explicit timezone")
    return parsed.astimezone(timezone.utc)


def kst_day(value: datetime | None = None) -> Any:
    """비용 한도의 '하루'는 KST 날짜다."""
    return (value or now_utc()).astimezone(KST).date()


def plus_hours(value: datetime, hours: float) -> datetime:
    return value + timedelta(hours=hours)

"""PostgreSQL 연결. 작업기는 ai_reply 스키마만 읽고 쓴다(모든 SQL 이 스키마를 명시한다)."""
from __future__ import annotations

from typing import Any

import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb


def connect(url: str) -> psycopg.Connection:
    """autocommit 연결. 여러 문장을 묶을 때는 `with conn.transaction():` 을 쓴다."""
    if not url:
        raise RuntimeError("REPLY_AI_DATABASE_URL is not set")
    return psycopg.connect(url, autocommit=True, row_factory=dict_row, application_name="reply-ai-worker")


def jsonb(value: Any) -> Jsonb:
    return Jsonb(value)

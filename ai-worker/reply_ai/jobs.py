"""ai_reply.ai_job 큐. SKIP LOCKED 로 한 작업기만 작업을 잡고, lease 소유자만 결과를 쓴다(fencing)."""
from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import Any

from . import errors
from .db import jsonb

RETRY_CAP_SECONDS = 300.0


@dataclass
class Job:
    id: int
    job_key: str
    job_type: str
    status: str
    payload: dict[str, Any]
    priority: int
    attempt_count: int
    max_attempts: int
    lease_owner: str | None
    result: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_row(cls, row: dict[str, Any]) -> "Job":
        return cls(id=row["id"], job_key=row["job_key"], job_type=row["job_type"], status=row["status"],
                   payload=row["payload"] or {}, priority=row["priority"], attempt_count=row["attempt_count"],
                   max_attempts=row["max_attempts"], lease_owner=row["lease_owner"], result=row["result"] or {})

    def stages(self) -> dict[str, Any]:
        return (self.result or {}).get("stages") or {}


def enqueue(conn, job_key: str, job_type: str, payload: dict[str, Any], priority: int = 0, max_attempts: int = 3) -> bool:
    """같은 job_key 는 한 번만 생긴다. 새로 넣었으면 True."""
    row = conn.execute(
        """INSERT INTO ai_reply.ai_job (job_key, job_type, status, payload, priority, max_attempts, next_attempt_at)
           VALUES (%s, %s, 'QUEUED', %s, %s, %s, now())
           ON CONFLICT (job_key) DO NOTHING RETURNING id""",
        (job_key, job_type, jsonb(payload), priority, max_attempts)).fetchone()
    return row is not None


CLAIM_SQL = """
WITH next AS (
    SELECT id FROM ai_reply.ai_job
     WHERE ((status IN ('QUEUED', 'RETRY_WAIT') AND next_attempt_at <= now())
            OR (status = 'RUNNING' AND lease_until < now()))
       AND (%(types)s::text[] IS NULL OR job_type = ANY(%(types)s::text[]))
     ORDER BY priority DESC, id
     FOR UPDATE SKIP LOCKED
     LIMIT 1)
UPDATE ai_reply.ai_job j
   SET status = 'RUNNING', lease_owner = %(owner)s, lease_until = now() + make_interval(secs => %(lease)s),
       attempt_count = j.attempt_count + 1, started_at = coalesce(j.started_at, now()), updated_at = now()
  FROM next
 WHERE j.id = next.id
RETURNING j.*
"""


def claim(conn, owner: str, lease_seconds: int, job_types: list[str] | None = None) -> Job | None:
    """대기 작업 또는 lease 가 끝난 실행 중 작업(작업기 중단)을 하나 잡는다. 잡을 때마다 attempt_count 가 오른다."""
    row = conn.execute(CLAIM_SQL, {"owner": owner, "lease": lease_seconds, "types": job_types}).fetchone()
    return Job.from_row(row) if row else None


def heartbeat(conn, job_id: int, owner: str, lease_seconds: int) -> bool:
    row = conn.execute(
        """UPDATE ai_reply.ai_job SET lease_until = now() + make_interval(secs => %s), updated_at = now()
            WHERE id = %s AND lease_owner = %s AND status = 'RUNNING' RETURNING id""",
        (lease_seconds, job_id, owner)).fetchone()
    return row is not None


def lock_owned(conn, job_id: int, owner: str) -> None:
    """트랜잭션 안에서 호출한다. lease 소유가 아니면 LeaseLost(결과를 쓰지 않는다)."""
    row = conn.execute(
        """SELECT id FROM ai_reply.ai_job WHERE id = %s AND lease_owner = %s AND status = 'RUNNING'
              AND lease_until > now() FOR UPDATE""",
        (job_id, owner)).fetchone()
    if row is None:
        raise errors.LeaseLost(f"job {job_id}")


def save_stage(conn, job_id: int, owner: str, stage: str, value: dict[str, Any]) -> None:
    """이미 받은 유효 단계 결과를 남긴다. 재개해도 이 단계는 다시 호출하지 않는다."""
    row = conn.execute(
        """UPDATE ai_reply.ai_job
              SET result = jsonb_set(coalesce(result, '{}'::jsonb), '{stages}',
                                     coalesce(result -> 'stages', '{}'::jsonb) || jsonb_build_object(%s::text, %s::jsonb)),
                  updated_at = now()
            WHERE id = %s AND lease_owner = %s AND status = 'RUNNING' RETURNING id""",
        (stage, jsonb(value), job_id, owner)).fetchone()
    if row is None:
        raise errors.LeaseLost(f"job {job_id}")


def succeed(conn, job_id: int, owner: str, result: dict[str, Any]) -> None:
    """lock_owned 와 같은 트랜잭션에서 결과 저장과 함께 호출한다. 단계 캐시는 남긴다."""
    row = conn.execute(
        """UPDATE ai_reply.ai_job
              SET status = 'SUCCEEDED', result = coalesce(result, '{}'::jsonb) || %s::jsonb,
                  lease_owner = NULL, lease_until = NULL, failure_code = NULL, failure_detail = NULL,
                  finished_at = now(), updated_at = now()
            WHERE id = %s AND lease_owner = %s AND status = 'RUNNING' RETURNING id""",
        (jsonb(result), job_id, owner)).fetchone()
    if row is None:
        raise errors.LeaseLost(f"job {job_id}")


def cancel(conn, job_id: int, owner: str, code: str, detail: str = "") -> None:
    conn.execute(
        """UPDATE ai_reply.ai_job
              SET status = 'CANCELLED', failure_code = %s, failure_detail = %s, lease_owner = NULL, lease_until = NULL,
                  finished_at = now(), updated_at = now()
            WHERE id = %s AND lease_owner = %s AND status = 'RUNNING'""",
        (code, detail[:300], job_id, owner))


def backoff_seconds(attempt: int, retry_after: float | None) -> float:
    if retry_after is not None and retry_after >= 0:
        return min(retry_after, RETRY_CAP_SECONDS)
    base = 5.0 * (2 ** max(0, attempt - 1))
    return min(base + random.uniform(0, base / 2), 60.0)


def fail(conn, job: Job, owner: str, failure: errors.JobFailure) -> str:
    """전송 실패는 시도 횟수 안에서 RETRY_WAIT, 그 밖은 FAILED(= DLQ). 남긴 상태를 돌려준다."""
    retry = failure.retryable and job.attempt_count < job.max_attempts
    code = failure.code
    if failure.retryable and not retry:
        code = errors.TRANSPORT_EXHAUSTED if failure.code == errors.TRANSPORT_EXHAUSTED else failure.code
    status = "RETRY_WAIT" if retry else "FAILED"
    delay = backoff_seconds(job.attempt_count, failure.retry_after) if retry else 0
    conn.execute(
        """UPDATE ai_reply.ai_job
              SET status = %s, next_attempt_at = now() + make_interval(secs => %s), failure_code = %s, failure_detail = %s,
                  lease_owner = NULL, lease_until = NULL, updated_at = now(),
                  finished_at = CASE WHEN %s = 'FAILED' THEN now() ELSE finished_at END
            WHERE id = %s AND lease_owner = %s AND status = 'RUNNING'""",
        (status, delay, code, failure.detail[:300], status, job.id, owner))
    return status

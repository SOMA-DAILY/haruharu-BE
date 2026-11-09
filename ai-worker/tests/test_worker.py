"""작업기 본체: heartbeat 로 긴 작업의 lease 를 유지하고, 알 수 없는 오류는 재시도하지 않는다."""
import threading
import time

import pytest

from reply_ai import errors, jobs
from reply_ai.config import Settings
from reply_ai.db import connect
from reply_ai.provider import ScriptedProvider
from reply_ai.worker import Worker

pytestmark = pytest.mark.pg


def slow_handler(ctx):
    time.sleep(2.5)  # lease(2초)보다 오래 걸리는 작업
    with ctx.conn.transaction():
        jobs.lock_owned(ctx.conn, ctx.job.id, ctx.owner)
        jobs.succeed(ctx.conn, ctx.job.id, ctx.owner, {"ok": True})


def boom(ctx):
    raise KeyError("bug")


def make_worker(database_url, handlers):
    settings = Settings(database_url=database_url, lease_seconds=2, heartbeat_seconds=1, poll_seconds=0.1, concurrency=2)
    return Worker(settings, provider=ScriptedProvider({}), connect_fn=lambda: connect(database_url), handlers=handlers)


def test_heartbeat_keeps_lease_for_long_job(conn, database_url):
    jobs.enqueue(conn, "slow", "SLOW", {})
    worker = make_worker(database_url, {"SLOW": slow_handler})
    worker.run_once(conn, "w1")
    assert conn.execute("SELECT status FROM ai_reply.ai_job").fetchone()["status"] == "SUCCEEDED"


def test_unexpected_error_fails_without_retry(conn, database_url):
    jobs.enqueue(conn, "boom", "BOOM", {})
    make_worker(database_url, {"BOOM": boom}).run_once(conn, "w1")
    row = conn.execute("SELECT status, failure_code, failure_detail FROM ai_reply.ai_job").fetchone()
    assert row == {"status": "FAILED", "failure_code": errors.INTERNAL_ERROR, "failure_detail": "KeyError"}


def test_serve_processes_jobs_concurrently_and_stops(conn, database_url):
    for i in range(3):
        jobs.enqueue(conn, f"fast-{i}", "FAST", {})

    def fast(ctx):
        with ctx.conn.transaction():
            jobs.lock_owned(ctx.conn, ctx.job.id, ctx.owner)
            jobs.succeed(ctx.conn, ctx.job.id, ctx.owner, {})

    worker = make_worker(database_url, {"FAST": fast})
    stop = threading.Event()
    thread = threading.Thread(target=worker.serve, args=(stop,), daemon=True)
    thread.start()
    deadline = time.time() + 10
    while time.time() < deadline:
        if conn.execute("SELECT count(*) AS n FROM ai_reply.ai_job WHERE status = 'SUCCEEDED'").fetchone()["n"] == 3:
            break
        time.sleep(0.1)
    stop.set()
    thread.join(timeout=15)
    assert not thread.is_alive()
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.ai_job WHERE status = 'SUCCEEDED'").fetchone()["n"] == 3

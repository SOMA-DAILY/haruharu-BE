"""작업 대기열(lease·fencing·재시도)과 비용 한도. PostgreSQL 통합."""
import pytest

from reply_ai import budget, errors, jobs
from reply_ai.config import Settings
from reply_ai.db import connect

pytestmark = pytest.mark.pg


def test_async02_same_job_key_enqueued_three_times_is_one_job(conn):
    """ASYNC02: 같은 작업이 세 번 전달돼도 작업은 하나."""
    assert jobs.enqueue(conn, "analysis:1", "ANALYSIS", {"analysis_input_id": 1})
    assert not jobs.enqueue(conn, "analysis:1", "ANALYSIS", {"analysis_input_id": 1})
    assert not jobs.enqueue(conn, "analysis:1", "ANALYSIS", {"analysis_input_id": 1})
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.ai_job").fetchone()["n"] == 1


def test_skip_locked_claim_gives_job_to_one_worker_only(conn, database_url):
    jobs.enqueue(conn, "k1", "ANALYSIS", {})
    other = connect(database_url)
    try:
        with conn.transaction():
            first = jobs.claim(conn, "owner-a", 180)
            second = jobs.claim(other, "owner-b", 180)
        assert first is not None and second is None
    finally:
        other.close()


def test_fencing_only_lease_owner_can_finish(conn):
    jobs.enqueue(conn, "k1", "ANALYSIS", {})
    job = jobs.claim(conn, "owner-a", 180)
    with pytest.raises(errors.LeaseLost):
        with conn.transaction():
            jobs.lock_owned(conn, job.id, "owner-b")
    with pytest.raises(errors.LeaseLost):
        jobs.save_stage(conn, job.id, "owner-b", "analysis", {"x": 1})
    with conn.transaction():
        jobs.lock_owned(conn, job.id, "owner-a")
        jobs.succeed(conn, job.id, "owner-a", {"ok": True})
    assert conn.execute("SELECT status FROM ai_reply.ai_job").fetchone()["status"] == "SUCCEEDED"


def test_expired_lease_is_reclaimed_and_old_owner_is_fenced(conn):
    jobs.enqueue(conn, "k1", "ANALYSIS", {})
    job = jobs.claim(conn, "dead-worker", 180)
    conn.execute("UPDATE ai_reply.ai_job SET lease_until = now() - interval '1 second' WHERE id = %s", (job.id,))
    again = jobs.claim(conn, "new-worker", 180)
    assert again.id == job.id and again.attempt_count == 2
    with pytest.raises(errors.LeaseLost):
        jobs.save_stage(conn, job.id, "dead-worker", "analysis", {})


def test_transport_retries_then_fails_and_semantic_fails_at_once(conn):
    jobs.enqueue(conn, "k1", "ANALYSIS", {}, max_attempts=3)
    for attempt in (1, 2):
        job = jobs.claim(conn, "w", 180)
        assert jobs.fail(conn, job, "w", errors.transport("429", retry_after=0)) == "RETRY_WAIT"
        conn.execute("UPDATE ai_reply.ai_job SET next_attempt_at = now()")
    job = jobs.claim(conn, "w", 180)
    assert job.attempt_count == 3
    assert jobs.fail(conn, job, "w", errors.transport("500")) == "FAILED"
    row = conn.execute("SELECT status, failure_code FROM ai_reply.ai_job").fetchone()
    assert row == {"status": "FAILED", "failure_code": errors.TRANSPORT_EXHAUSTED}
    jobs.enqueue(conn, "k2", "ANALYSIS", {})
    job = jobs.claim(conn, "w", 180)
    assert jobs.fail(conn, job, "w", errors.JobFailure(errors.REFUSAL)) == "FAILED"


def test_stage_cache_survives_reclaim(conn):
    jobs.enqueue(conn, "k1", "ANALYSIS", {})
    job = jobs.claim(conn, "a", 180)
    jobs.save_stage(conn, job.id, "a", "analysis", {"output": {"x": 1}})
    conn.execute("UPDATE ai_reply.ai_job SET lease_until = now() - interval '1 second'")
    again = jobs.claim(conn, "b", 180)
    assert again.stages()["analysis"] == {"output": {"x": 1}}


def test_daily_budget_reserve_settle_and_exceed(conn):
    settings = Settings(daily_budget_usd=0.10)
    first = budget.reserve_online(conn, settings, 0.06)
    with pytest.raises(errors.JobFailure) as info:
        budget.reserve_online(conn, settings, 0.06)
    assert info.value.code == errors.BUDGET_EXCEEDED
    budget.settle(conn, first, 0.01)
    second = budget.reserve_online(conn, settings, 0.06)
    budget.settle(conn, second, 0.02)
    row = conn.execute("SELECT reserved_usd, spent_usd FROM ai_reply.budget_day").fetchone()
    assert float(row["reserved_usd"]) == 0 and abs(float(row["spent_usd"]) - 0.03) < 1e-9

"""작업기 본체: 동시 실행 N개, lease 180초·30초 heartbeat, lease 소유자만 완료를 기록한다."""
from __future__ import annotations

import json
import logging
import os
import socket
import threading
import time
import uuid
from typing import Any, Callable

import psycopg

from . import errors, jobs, media, pipeline
from .config import Settings
from .db import connect
from .embedding import Embedder, make_embedder
from .provider import OpenAIProvider, Provider

log = logging.getLogger("reply_ai.worker")

_SAFE_EXTRA = {"job_id", "job_type", "job_key_hash", "status", "failure_code", "attempt", "stage", "provider_request_id",
               "input_tokens", "output_tokens", "cost_usd", "ms", "owner", "batch_run_id", "decision", "count", "worker"}


class JsonFormatter(logging.Formatter):
    """한 줄 JSON. 허용한 칸(id·코드·토큰·비용·시간)만 싣는다. 원문이 실릴 수 있는 칸은 받지 않는다."""

    def format(self, record: logging.LogRecord) -> str:
        body: dict[str, Any] = {"ts": time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(record.created)) + "Z",
                                "level": record.levelname, "logger": record.name, "event": record.getMessage()}
        for key in _SAFE_EXTRA:
            if hasattr(record, key):
                value = getattr(record, key)
                if isinstance(value, (str, int, float, bool)) or value is None:
                    body[key] = value
        if record.exc_info:
            body["exception"] = record.exc_info[0].__name__ if record.exc_info[0] else None
        return json.dumps(body, ensure_ascii=False)


def setup_logging(level: str = "INFO") -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger("reply_ai")
    root.handlers[:] = [handler]
    root.setLevel(level)
    root.propagate = False


class Worker:
    def __init__(self, settings: Settings, provider: Provider | None = None,
                 embedder_factory: Callable[[], Embedder] | None = None, fetcher: media.MediaFetcher | None = None,
                 connect_fn: Callable[[], Any] | None = None, handlers: dict[str, Callable] | None = None):
        self.settings = settings
        self._provider = provider
        self._embedder: Embedder | None = None
        self._embedder_factory = embedder_factory
        self.fetcher = fetcher or media.MediaFetcher(settings)
        self.connect_fn = connect_fn or (lambda: connect(settings.database_url))
        self.handlers = dict(handlers or pipeline.HANDLERS)
        if "FEEDBACK_BATCH" not in self.handlers:
            from .feedback.engine import run_feedback_job
            self.handlers["FEEDBACK_BATCH"] = run_feedback_job
        self._lock = threading.Lock()

    @property
    def provider(self) -> Provider:
        with self._lock:
            if self._provider is None:
                self._provider = OpenAIProvider(self.settings)
            return self._provider

    def embedder(self) -> Embedder:
        with self._lock:
            if self._embedder is None:
                self._embedder = (self._embedder_factory or (lambda: make_embedder(self.settings)))()
            return self._embedder

    def owner(self, index: int = 0) -> str:
        prefix = self.settings.worker_id or socket.gethostname()
        return f"{prefix}:{os.getpid()}:{index}:{uuid.uuid4().hex[:8]}"

    def run_once(self, conn, owner: str, job_types: list[str] | None = None) -> jobs.Job | None:
        job = jobs.claim(conn, owner, self.settings.lease_seconds, job_types)
        if job is None:
            return None
        self.process(conn, job, owner)
        return job

    def process(self, conn, job: jobs.Job, owner: str) -> None:
        started = time.monotonic()
        extra = {"job_id": job.id, "job_type": job.job_type, "attempt": job.attempt_count, "owner": owner}
        log.info("job_claimed", extra=extra)
        if job.attempt_count > job.max_attempts:
            jobs.fail(conn, job, owner, errors.JobFailure(errors.ATTEMPTS_EXHAUSTED, "lease expired repeatedly"))
            log.warning("job_failed", extra={**extra, "failure_code": errors.ATTEMPTS_EXHAUSTED})
            return
        handler = self.handlers.get(job.job_type)
        if handler is None:
            jobs.fail(conn, job, owner, errors.JobFailure(errors.INTERNAL_ERROR, f"unknown job_type {job.job_type}"))
            return
        stop = threading.Event()
        beat = threading.Thread(target=self._heartbeat, args=(job.id, owner, stop), daemon=True)
        beat.start()
        ctx = pipeline.JobContext(conn=conn, job=job, owner=owner, settings=self.settings, provider=self.provider,
                                  embedder_factory=self.embedder, fetcher=self.fetcher)
        try:
            handler(ctx)
            log.info("job_done", extra={**extra, "ms": int((time.monotonic() - started) * 1000),
                                        "cost_usd": ctx.usage_summary()["cost_usd"]})
        except errors.LeaseLost:
            log.warning("job_lease_lost", extra=extra)
        except errors.JobFailure as failure:
            status = jobs.fail(conn, job, owner, failure)
            log.warning("job_failed", extra={**extra, "failure_code": failure.code, "status": status})
        except psycopg.OperationalError:
            log.exception("job_db_error", extra=extra)
            try:
                jobs.fail(conn, job, owner, errors.JobFailure(errors.INTERNAL_ERROR, "database", retryable=True))
            except psycopg.Error:
                pass  # 연결이 끊겼으면 lease 만료 후 다른 작업기가 다시 잡는다.
        except Exception as error:  # 버그: 재시도하지 않고 원인 종류만 남긴다.
            log.exception("job_error", extra=extra)
            jobs.fail(conn, job, owner, errors.JobFailure(errors.INTERNAL_ERROR, type(error).__name__))
        finally:
            stop.set()
            beat.join(timeout=5)

    def _heartbeat(self, job_id: int, owner: str, stop: threading.Event) -> None:
        try:
            conn = self.connect_fn()
        except Exception:
            log.exception("heartbeat_connect_failed", extra={"job_id": job_id})
            return
        try:
            while not stop.wait(self.settings.heartbeat_seconds):
                if not jobs.heartbeat(conn, job_id, owner, self.settings.lease_seconds):
                    log.warning("heartbeat_lost", extra={"job_id": job_id})
                    return
        except Exception:
            log.exception("heartbeat_failed", extra={"job_id": job_id})
        finally:
            conn.close()

    def serve(self, stop: threading.Event | None = None, job_types: list[str] | None = None) -> None:
        stop = stop or threading.Event()
        threads = [threading.Thread(target=self._loop, args=(i, stop, job_types), daemon=True)
                   for i in range(max(1, self.settings.concurrency))]
        if self.settings.feedback_batch_enabled:
            threads.append(threading.Thread(target=self._schedule_batches, args=(stop,), daemon=True))
        for thread in threads:
            thread.start()
        log.info("worker_started", extra={"count": self.settings.concurrency})
        try:
            while not stop.is_set():
                stop.wait(1.0)
        except KeyboardInterrupt:
            stop.set()
        for thread in threads:
            thread.join(timeout=10)

    def _loop(self, index: int, stop: threading.Event, job_types: list[str] | None) -> None:
        conn = None
        while not stop.is_set():
            try:
                if conn is None or conn.closed:
                    conn = self.connect_fn()
                owner = self.owner(index)
                if self.run_once(conn, owner, job_types) is None:
                    stop.wait(self.settings.poll_seconds)
            except psycopg.Error:
                log.exception("worker_db_error", extra={"worker": index})
                conn = None
                stop.wait(5.0)

    def _schedule_batches(self, stop: threading.Event) -> None:
        from .feedback.engine import schedule
        while not stop.is_set():
            try:
                conn = self.connect_fn()
                try:
                    result = schedule(conn, self.settings)
                    if result:
                        log.info("batch_scheduled", extra={"batch_run_id": result.get("batch_run_id"),
                                                           "decision": result.get("status")})
                finally:
                    conn.close()
            except Exception:
                log.exception("batch_schedule_failed")
            stop.wait(self.settings.batch_check_minutes * 60)

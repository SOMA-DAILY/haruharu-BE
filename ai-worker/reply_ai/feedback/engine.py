"""실제 답장 기반 피드백 배치(12·13장). 연구 feedback_batch 규칙을 PostgreSQL 로 옮겼다.

1. 실제 답장 판정: 추천을 숨기고 영상 문맥 + 실제 답장으로 적절성(05).
2. 방향 포함 판정: 검수 전 후보와 최종 카드 각각(06).
3. 사례와 개선안: 적절·고신뢰·의미 안정·방향 명확한 관찰만 사례로 쌓고, 같은 원인 ≥3 이면 한 모듈 지시 ≤1200자 제안(07).
4. 고정 검증: 같은 입력으로 baseline/candidate 를 새로 생성 → 결과·SHA 동결 → 그 뒤에 실제 답장으로 방향 판정,
   카드 적절성은 실제 답장을 숨기고 판정(08). 모든 사례에서 회귀가 없고 하나라도 개선돼야 통과.
통과해도 자동 적용 스위치가 꺼져 있으면 VALIDATED 로 둔다. 켜져 있으면 활성 revision CAS 로만 바꾼다.
첫 유효 판정은 그대로 보존하고(같은 입력·프롬프트·모델이면 다시 부르지 않는다) 불확실하다고 다시 판정하지 않는다.
"""
from __future__ import annotations

import logging
import random
import re
import time
from collections import Counter
from datetime import timedelta
from typing import Any, Callable

from .. import analysis, budget, depth, errors, generation, jobs, tokens, versions
from ..config import Settings
from ..db import jsonb
from ..provider import ModelRequest, Provider
from ..resources import package_config
from ..util import canonical, iso, now_utc, sha256_json, sha256_text

log = logging.getLogger("reply_ai.feedback")

REASONS = ("candidate_absent", "gate_dropped")
MODULE_PROMPT = {"generation": "generic", "verification": "gate"}
MODULE_REASON = {"generation": "candidate_absent", "verification": "gate_dropped"}


class Pending(Exception):
    """다시 시도하면 이어갈 수 있는 상태(전송 실패 등). 배치는 PENDING 으로 남는다."""


class Inconclusive(Exception):
    def __init__(self, code: str, message: str = ""):
        self.code = code
        super().__init__(message or code)


def batch_config() -> dict[str, Any]:
    return package_config()["batch"]


def allowed_provenance(settings: Settings) -> list[str]:
    return ["QA", "SYNTHETIC"] + (["REAL_USER"] if settings.real_user_feedback_enabled else [])


# ───────────────────────── 스케줄 ─────────────────────────

def record_closures(conn, settings: Settings) -> int:
    rows = conn.execute(
        """INSERT INTO ai_reply.feedback_closure (episode_id, closed_at)
           SELECT e.id, e.closed_at FROM ai_reply.reply_episode e
            WHERE e.closed_at IS NOT NULL AND e.provenance = ANY(%s)
              AND NOT EXISTS (SELECT 1 FROM ai_reply.feedback_closure c WHERE c.episode_id = e.id)
            ORDER BY e.closed_at, e.id
           ON CONFLICT DO NOTHING RETURNING seq""", (allowed_provenance(settings),)).fetchall()
    return len(rows)


SCHEDULE_LOCK = 558_0001  # 여러 작업기가 동시에 배치를 만들지 않게 하는 advisory lock 키


def schedule(conn, settings: Settings, force: bool = False, now=None, reason: str | None = None) -> dict[str, Any] | None:
    """진행 중 배치는 그대로 재개하고, 조건(새 종료 20개 또는 가장 오래된 것 24시간)이나 force 면 새 배치를 만든다.

    운영은 정기 실행(run_scheduled, 한 달에 한 번)이 force 로 부른다. 작업기 안 매시간 스케줄은 로컬·시험용이다.
    """
    with conn.transaction():
        if not conn.execute("SELECT pg_try_advisory_xact_lock(%s) AS ok", (SCHEDULE_LOCK,)).fetchone()["ok"]:
            return None
        return _schedule(conn, settings, force, now or now_utc(), reason)


def _schedule(conn, settings: Settings, force: bool, now, reason: str | None = None) -> dict[str, Any] | None:
    record_closures(conn, settings)
    pending = conn.execute("SELECT * FROM ai_reply.batch_run WHERE status = 'PENDING' ORDER BY id LIMIT 1").fetchone()
    if pending:
        ensure_job(conn, pending["id"])
        return {"batch_run_id": pending["id"], "status": "resumed"}
    lo = conn.execute("""SELECT coalesce(max(hi_closure_seq), 0) AS lo FROM ai_reply.batch_run
                          WHERE status <> 'PENDING'""").fetchone()["lo"]
    rows = conn.execute("""SELECT c.seq, c.episode_id, c.closed_at FROM ai_reply.feedback_closure c
                            WHERE c.seq > %s ORDER BY c.seq""", (lo,)).fetchall()
    if not rows:
        return None
    config = batch_config()
    oldest = min(r["closed_at"] for r in rows)
    by_count = len(rows) >= int(config["closed_episode_threshold"])
    by_wait = now - oldest >= timedelta(hours=float(config["max_wait_hours"]))
    if not (force or by_count or by_wait):
        return None
    active = versions.active(conn)
    if active is None:
        return None
    # schedule() 의 트랜잭션·advisory lock 안이다. cutoff·baseline 을 이 순간 값으로 고정한다.
    batch = conn.execute(
        """INSERT INTO ai_reply.batch_run (status, trigger_reason, cutoff_closed_at, lo_episode_id, hi_episode_id,
               baseline_version_id, baseline_revision, gallery_snapshot_id, budget_usd, lo_closure_seq, hi_closure_seq, audit)
           VALUES ('PENDING', %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id""",
        (reason or ("COUNT" if by_count else "MAX_WAIT" if by_wait else "MANUAL"), now, min(r["episode_id"] for r in rows),
         max(r["episode_id"] for r in rows), active["version_id"], active["revision"], active["gallery_snapshot_id"],
         settings.feedback_budget_usd, lo, rows[-1]["seq"],
         jsonb([{"at": iso(now), "action": "created", "closures": len(rows)}]))).fetchone()
    ensure_job(conn, batch["id"])
    return {"batch_run_id": batch["id"], "status": "created", "closures": len(rows)}


def ensure_job(conn, batch_run_id: int) -> None:
    key = f"feedback:{batch_run_id}"
    if not jobs.enqueue(conn, key, "FEEDBACK_BATCH", {"batch_run_id": batch_run_id}, priority=-10):
        # 같은 배치를 재개한다: 끝난(실패한) 작업 행을 다시 대기로 돌린다. 판정 캐시는 그대로 쓴다.
        conn.execute("""UPDATE ai_reply.ai_job SET status = 'QUEUED', attempt_count = 0, next_attempt_at = now(),
                               failure_code = NULL, failure_detail = NULL, updated_at = now()
                         WHERE job_key = %s AND status IN ('FAILED', 'SUCCEEDED', 'CANCELLED')""", (key,))


TERMINAL_JOB_STATES = ("SUCCEEDED", "FAILED", "CANCELLED")


def run_scheduled(conn, settings: Settings, worker, *, deadline_seconds: float = 7200.0,
                  sleep: Callable[[float], None] = time.sleep, clock: Callable[[], float] = time.monotonic) -> dict[str, Any]:
    """정기 개선 루프 한 번(운영: 한 달에 한 번 EventBridge Scheduler → ECS 작업, 사용자 결정 2026-11-09).

    지난 배치 뒤 새로 닫힌 관찰로 배치를 만들거나 남은 배치를 이어 받아, 이 프로세스에서 끝까지 돌리고 끝낸다.
    상시 작업기는 FEEDBACK_BATCH 를 받지 않으므로 긴 평가가 사용자 추천을 막지 않는다. 새 관찰이 없으면 아무것도 안 한다.
    전송 실패로 남으면 마감 안에서 재시도 시각을 기다리고, 그래도 남으면 다음 정기 실행이 이어 받는다.
    """
    scheduled = schedule(conn, settings, force=True, reason="SCHEDULED")
    if not scheduled:
        return {"status": "idle"}
    batch_id = int(scheduled["batch_run_id"])
    key = f"feedback:{batch_id}"
    started = clock()
    job_row = None
    while True:
        claimed = worker.run_once(conn, worker.owner(), ["FEEDBACK_BATCH"])
        job_row = conn.execute("SELECT status, next_attempt_at FROM ai_reply.ai_job WHERE job_key = %s", (key,)).fetchone()
        if job_row is None or job_row["status"] in TERMINAL_JOB_STATES:
            break
        if claimed is not None:
            continue
        if job_row["status"] == "RUNNING":
            wait = 30.0  # 다른 프로세스가 lease 를 잡고 있다(겹친 실행). 끝나거나 lease 가 풀릴 때까지 기다린다.
        else:
            due = job_row["next_attempt_at"]
            wait = max(1.0, (due - now_utc()).total_seconds()) if due else 5.0
        if clock() - started + wait > deadline_seconds:
            break
        sleep(min(wait, 600.0))
    batch = conn.execute("""SELECT status, trigger_reason, result ->> 'decision' AS decision, spent_usd, candidate_version_id
                              FROM ai_reply.batch_run WHERE id = %s""", (batch_id,)).fetchone()
    return {"status": batch["status"], "batch_run_id": batch_id, "trigger_reason": batch["trigger_reason"],
            "decision": batch["decision"], "spent_usd": batch["spent_usd"], "candidate_version_id": batch["candidate_version_id"],
            "job_status": job_row["status"] if job_row else None, "scheduled": scheduled.get("status")}


def run_feedback_job(ctx) -> None:
    """FEEDBACK_BATCH 작업. 배치가 PENDING 으로 남으면 전송 재시도 대상(작업 RETRY_WAIT)."""
    engine = Engine(ctx.conn, ctx.settings, ctx.provider, owner=ctx.owner, job=ctx.job)
    outcome = engine.tick(int(ctx.job.payload["batch_run_id"]))
    with ctx.conn.transaction():
        jobs.lock_owned(ctx.conn, ctx.job.id, ctx.owner)
        if outcome.get("status") == "PENDING":
            raise errors.JobFailure(errors.TRANSPORT_EXHAUSTED, outcome.get("error", "pending"), retryable=True, retry_after=600)
        jobs.succeed(ctx.conn, ctx.job.id, ctx.owner, {"batch_run_id": outcome.get("batch_run_id"), "status": outcome.get("status")})


# ───────────────────────── 엔진 ─────────────────────────

class Engine:
    def __init__(self, conn, settings: Settings, provider: Provider, owner: str = "cli", job: jobs.Job | None = None,
                 rng_seed: str = "feedback"):
        self.conn, self.settings, self.provider, self.owner, self.job = conn, settings, provider, owner, job
        self.batch: dict[str, Any] = {}
        self.baseline: dict[str, Any] = {}
        self.rng_seed = rng_seed

    def _lease(self) -> bool:
        """같은 배치를 두 곳에서 동시에 돌리지 않는다(작업기 작업과 수동 tick). 모델 호출마다 연장한다."""
        row = self.conn.execute(
            """UPDATE ai_reply.batch_run SET lease_owner = %(owner)s, lease_until = now() + interval '10 minutes', updated_at = now()
                WHERE id = %(id)s AND status = 'PENDING'
                  AND (lease_until IS NULL OR lease_until < now() OR lease_owner = %(owner)s) RETURNING id""",
            {"owner": self.owner, "id": self.batch["id"]}).fetchone()
        return row is not None

    # 모델 호출 — 판정은 같은 단계·입력·프롬프트·모델이면 첫 유효 결과를 재사용한다.
    def _model(self, stage: str, prompt: str, payload: dict[str, Any]) -> dict[str, Any]:
        if not self._lease():
            raise Pending("batch lease lost")
        request = ModelRequest(stage, prompt, payload, max_output_tokens=self.settings.max_output_tokens(stage))
        estimate = tokens.estimate_input(prompt, canonical(payload))
        reservation = budget.reserve_batch(self.conn, self.batch["id"], budget.estimate_usd(self.settings, estimate, request.max_output_tokens))
        try:
            result = self.provider.call(request)
        except errors.JobFailure as failure:
            budget.settle(self.conn, reservation, failure.cost_usd)
            if failure.retryable:
                raise Pending(failure.code) from None
            raise
        budget.settle(self.conn, reservation, result.cost_usd)
        return result.output

    def judge(self, stage: str, payload: dict[str, Any], *, episode_id: int | None = None, validation_run_id: int | None = None,
              case_key: str | None = None) -> dict[str, Any]:
        analysis.forbid_evaluation_fields({k: v for k, v in payload.items() if k != "actual_reply"})
        prompt = self.baseline["prompts_json"][stage]
        input_sha, prompt_sha = sha256_json(payload), sha256_text(prompt)
        row = self.conn.execute(
            """SELECT output_json FROM ai_reply.feedback_judgment WHERE stage = %s AND input_sha = %s AND prompt_sha = %s
                  AND model = %s""", (stage.upper(), input_sha, prompt_sha, self.provider.model)).fetchone()
        if row:
            return row["output_json"]
        output = self._model(stage, prompt, payload)
        verdict = self._check(stage, output, payload)
        self.conn.execute(
            """INSERT INTO ai_reply.feedback_judgment (batch_run_id, episode_id, validation_run_id, case_key, stage, model,
                   prompt_sha, version_id, input_sha, output_json, output_sha, verdict)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) ON CONFLICT DO NOTHING""",
            (self.batch["id"], episode_id, validation_run_id, case_key, stage.upper(), self.provider.model, prompt_sha,
             self.baseline["version_id"], input_sha, jsonb(output), sha256_json(output), verdict))
        return output

    @staticmethod
    def _check(stage: str, output: dict[str, Any], payload: dict[str, Any]) -> str:
        if stage in ("actual_appropriateness", "card_quality"):
            return output["verdict"]
        if stage == "direction_match":
            raw_ids = {c["candidate_id"] for c in payload["candidates"]}
            final_ids = {c["card_id"] for c in payload["final_cards"]}
            for state, ids, allowed in (("raw", output["raw_ids"], raw_ids), ("final", output["final_ids"], final_ids)):
                if len(set(ids)) != len(ids) or not set(ids) <= allowed:
                    raise errors.semantic(f"direction {state} ids unknown/duplicate")
                if (output[state] == "included") != bool(ids):
                    raise errors.semantic(f"direction {state} ids contradict state")
            if output["final"] == "included" and output["raw"] == "absent":
                raise errors.semantic("final included but raw absent")
            return f"{output['raw']}/{output['final']}"
        return output.get("status", "")

    # 배치 실행
    def tick(self, batch_run_id: int) -> dict[str, Any]:
        batch = self.conn.execute("SELECT * FROM ai_reply.batch_run WHERE id = %s", (batch_run_id,)).fetchone()
        if batch is None:
            return {"status": "MISSING", "batch_run_id": batch_run_id}
        if batch["status"] != "PENDING":
            return {"status": batch["status"], "batch_run_id": batch_run_id}
        self.batch = batch
        self.baseline = versions.load(self.conn, batch["baseline_version_id"])
        if not self._lease():
            return {"status": "busy", "batch_run_id": batch_run_id}
        try:
            observations = [self.observe(episode) for episode in self.episodes()]
            summary = Counter(o["observation"] for o in observations)
            aggregates = self.aggregates()
            if not aggregates:
                return self.finish("KEPT_BASELINE", {"decision": "no_repeated_supported_reason", "observations": dict(summary)})
            proposal = self.proposal(aggregates)
            consumed = [case_id for a in aggregates for case_id in a["case_ids"]]
            if proposal["status"] != "proposed":
                return self.finish("KEPT_BASELINE", {"decision": "deferred", "observations": dict(summary),
                                                     "aggregates": _public(aggregates), "proposal": proposal}, consumed)
            suite = self.freeze_suite()
            if suite is None:
                # 검증 자료가 부족하면 기존 버전을 유지한다. 관찰은 소비하지 않아 다음 배치가 다시 쓸 수 있다.
                return self.finish("KEPT_BASELINE", {"decision": "validation_suite_insufficient", "observations": dict(summary),
                                                     "aggregates": _public(aggregates), "proposal": proposal})
            candidate = self.candidate_version(proposal)
            try:
                validation = self.validate(suite, candidate)
            except Inconclusive as sealed:
                return self.finish("INCONCLUSIVE", {"decision": "kept_baseline_inconclusive", "code": sealed.code,
                                                    "candidate_version_id": candidate["version_id"]}, consumed)
            if not validation["passed"]:
                self.conn.execute("UPDATE ai_reply.version_registry SET status = 'REJECTED' WHERE version_id = %s",
                                  (candidate["version_id"],))
                return self.finish("KEPT_BASELINE", {"decision": "kept_baseline", "validation": validation,
                                                     "candidate_version_id": candidate["version_id"]}, consumed)
            return self.activate_or_hold(candidate, validation, consumed)
        except Pending as pending:
            self.conn.execute("""UPDATE ai_reply.batch_run SET lease_owner = NULL, lease_until = NULL, updated_at = now(),
                                        audit = audit || %s WHERE id = %s""",
                              (jsonb([{"at": iso(now_utc()), "action": "pending", "code": str(pending)}]), batch_run_id))
            return {"status": "PENDING", "batch_run_id": batch_run_id, "error": str(pending)}
        except errors.JobFailure as failure:
            if failure.code == errors.BUDGET_EXCEEDED:
                return self.finish("KEPT_BASELINE", {"decision": "budget_exceeded"})
            raise

    def finish(self, status: str, result: dict[str, Any], consume_case_ids: list[int] | None = None) -> dict[str, Any]:
        with self.conn.transaction():
            if consume_case_ids:
                self.conn.execute("""UPDATE ai_reply.feedback_case SET consumed_by_batch_id = %s
                                      WHERE id = ANY(%s) AND consumed_by_batch_id IS NULL""", (self.batch["id"], consume_case_ids))
            self.conn.execute(
                """UPDATE ai_reply.batch_run SET status = %s, result = %s, finished_at = now(), updated_at = now(),
                          lease_owner = NULL, lease_until = NULL, audit = audit || %s,
                          candidate_version_id = coalesce(%s, candidate_version_id)
                    WHERE id = %s AND status = 'PENDING'""",
                (status, jsonb(result), jsonb([{"at": iso(now_utc()), "action": "finished", "status": status,
                                                "decision": result.get("decision")}]),
                 result.get("candidate_version_id"), self.batch["id"]))
        log.info("batch_finished", extra={"batch_run_id": self.batch["id"], "status": status, "decision": result.get("decision")})
        return {"status": status, "batch_run_id": self.batch["id"], **result}

    # 1·2단계: 관찰
    def episodes(self) -> list[dict[str, Any]]:
        return self.conn.execute(
            """SELECT e.*, c.seq, s.reply_text, s.message_id AS snapshot_message_id
                 FROM ai_reply.feedback_closure c JOIN ai_reply.reply_episode e ON e.id = c.episode_id
                 LEFT JOIN ai_reply.episode_reply_snapshot s ON s.episode_id = e.id
                WHERE c.seq > %s AND c.seq <= %s ORDER BY c.seq""",
            (self.batch["lo_closure_seq"] or 0, self.batch["hi_closure_seq"] or 0)).fetchall()

    def episode_material(self, episode: dict[str, Any]) -> dict[str, Any] | None:
        """episode 의 영상 문맥·검수 전 후보·최종 카드. 세트·분석이 무효면 None(판정하지 않는다)."""
        rset = self.conn.execute("SELECT * FROM ai_reply.recommendation_set WHERE set_id = %s", (episode["set_id"],)).fetchone()
        if rset is None:
            return None
        snapshot = self.conn.execute("SELECT * FROM ai_reply.analysis_snapshot WHERE id = %s", (rset["analysis_id"],)).fetchone()
        if snapshot is None or snapshot["status"] != "READY":
            return None
        source = self.conn.execute("SELECT * FROM ai_reply.analysis_input WHERE id = %s", (snapshot["analysis_input_id"],)).fetchone()
        attempt_ids = [rset["attempt_id"]]
        if rset["base_set_id"]:
            base = self.conn.execute("SELECT attempt_id FROM ai_reply.recommendation_set WHERE set_id = %s", (rset["base_set_id"],)).fetchone()
            if base:
                attempt_ids.insert(0, base["attempt_id"])
        raw: list[dict[str, Any]] = []
        generic_attempt = None
        for attempt in self.conn.execute("SELECT * FROM ai_reply.generation_attempt WHERE id = ANY(%s) ORDER BY id", (attempt_ids,)).fetchall():
            if attempt["stage"] == "GENERIC":
                generic_attempt = attempt
            raw.extend((attempt["candidates"] or {}).get("kept", []))
        return {"set": rset, "snapshot": snapshot, "source": source, "generic_attempt": generic_attempt,
                "raw": [{"candidate_id": c["candidate_id"], "category": c["category"], "title": c["title"], "example": c["example"]} for c in raw],
                "final": [{"card_id": c["card_id"], "category": c["category"], "title": c["title"], "example": c["example"]}
                          for c in rset["internal_cards"] or []],
                "context": context_of(snapshot, source, episode["viewer_id"])}

    def observe(self, episode: dict[str, Any]) -> dict[str, Any]:
        base = {"episode_id": episode["id"]}
        if episode["outcome"] != "REPLIED" or episode["attribution"] not in ("explicit", "inferred") or not episode["reply_text"]:
            # 답장이 없었던 사례는 분모로만 쓰고 정답 방향을 만들지 않는다.
            return {**base, "observation": "no_attributed_reply"}
        if self._protected(episode):
            return {**base, "observation": "protected"}
        material = self.episode_material(episode)
        if material is None:
            return {**base, "observation": "material_unavailable"}
        reply = {"text": episode["reply_text"]}
        try:
            quality = self.judge("actual_appropriateness", {"context": material["context"], "actual_reply": reply}, episode_id=episode["id"])
            direction = self.judge("direction_match", {"context": material["context"], "actual_reply": reply,
                                                       "candidates": material["raw"], "final_cards": self._shuffled(material["final"], episode["id"])},
                                   episode_id=episode["id"])
        except errors.JobFailure as failure:
            if failure.code == errors.BUDGET_EXCEEDED:
                raise
            return {**base, "observation": "judge_invalid", "code": failure.code}
        suitable = quality["verdict"] == "appropriate" and quality["confidence"] == "high" and quality["stable_meaning"]
        clear = suitable and direction["raw"] != "uncertain" and direction["final"] != "uncertain"
        if not clear:
            return {**base, "observation": "unsuitable_or_uncertain"}
        reason = "included" if direction["final"] == "included" else ("candidate_absent" if direction["raw"] == "absent" else "gate_dropped")
        self.conn.execute(
            """INSERT INTO ai_reply.feedback_case (episode_id, batch_run_id, provenance, room_id, viewer_id, reason_code, split,
                   reference_eligible)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s) ON CONFLICT (episode_id) DO NOTHING""",
            (episode["id"], self.batch["id"], episode["provenance"], episode["room_id"], episode["viewer_id"], reason,
             split_of(episode["room_id"]),
             # QA·합성 관찰은 참고 사례가 되지 않는다. 실제 사용자도 타 방 재사용 허가 경로가 생기기 전에는 내보내지 않는다.
             False))
        return {**base, "observation": reason}

    def _protected(self, episode: dict[str, Any]) -> bool:
        row = self.conn.execute(
            """SELECT 1 FROM ai_reply.protected_registry WHERE (kind = 'episode' AND value = %s) OR (kind = 'video' AND value = %s)
                OR (kind = 'reply' AND value = %s) LIMIT 1""",
            (str(episode["id"]), str(episode["video_id"]), str(episode["actual_reply_message_id"] or ""))).fetchone()
        return row is not None

    def _shuffled(self, cards: list[dict[str, Any]], salt: Any) -> list[dict[str, Any]]:
        """판정자에게 제시하는 카드 순서를 섞는다(결정적)."""
        order = list(cards)
        random.Random(sha256_json([self.rng_seed, salt])).shuffle(order)
        return order

    # 3단계: 원인 집계와 개선안
    def aggregates(self) -> list[dict[str, Any]]:
        rows = self.conn.execute(
            """SELECT f.id, f.reason_code FROM ai_reply.feedback_case f
                WHERE f.consumed_by_batch_id IS NULL AND f.reason_code = ANY(%s) AND f.provenance = ANY(%s)
                  AND f.batch_run_id <= %s ORDER BY f.id""",
            (list(REASONS), allowed_provenance(self.settings), self.batch["id"])).fetchall()
        by_reason: dict[str, list[int]] = {}
        for row in rows:
            by_reason.setdefault(row["reason_code"], []).append(row["id"])
        minimum = int(batch_config()["min_reason_count"])
        return [{"reason": r, "count": len(ids), "case_ids": ids} for r, ids in sorted(by_reason.items()) if len(ids) >= minimum]

    def proposal(self, aggregates: list[dict[str, Any]]) -> dict[str, Any]:
        if self.batch.get("patch"):
            return self.batch["patch"]
        prompts = self.baseline["prompts_json"]
        payload = {"aggregates": _public(aggregates),
                   "baseline": {"version_id": self.baseline["version_id"], "generation_instruction": prompts["generic"],
                                "verification_instruction": prompts["gate"]},
                   "bounds": {"allowed_modules": ["generation", "verification"], "max_patch_chars": int(batch_config()["max_patch_chars"])}}
        try:
            output = self._model("propose_patch", prompts["propose_patch"], payload)
        except errors.JobFailure as failure:
            if failure.code == errors.BUDGET_EXCEEDED:
                raise
            # 형식이 틀린 제안은 채택하지 않는다(보류). 같은 배치에서 다시 만들지 않는다.
            output = {"status": "deferred", "module": "none", "instruction": "", "reason_codes": [failure.code]}
        value = {"status": "deferred", "proposal": output}
        if output["status"] == "proposed":
            problem = self._patch_problem(output, aggregates)
            value = {"status": "proposed", "proposal": output} if problem is None else {"status": "deferred", "proposal": output, "reason": problem}
        self.conn.execute("UPDATE ai_reply.batch_run SET patch = %s, updated_at = now() WHERE id = %s AND patch IS NULL",
                          (jsonb(value), self.batch["id"]))
        self.batch["patch"] = value
        return value

    def _patch_problem(self, output: dict[str, Any], aggregates: list[dict[str, Any]]) -> str | None:
        module, instruction = output["module"], output["instruction"]
        if module not in MODULE_PROMPT:
            return "module"
        if not instruction.strip() or len(instruction) > int(batch_config()["max_patch_chars"]):
            return "instruction_bounds"
        if not any(a["reason"] == MODULE_REASON[module] for a in aggregates):
            return "target_module_has_no_supported_repeated_reason"
        if re.search(r"https?://|\b\d{4}-\d{2}-\d{2}\b|\b[0-9a-f]{8}-[0-9a-f-]{27,}\b", instruction, re.I):
            return "case_specific_identifier"
        case_ids = [i for a in aggregates for i in a["case_ids"]]
        rows = self.conn.execute(
            """SELECT f.episode_id, e.video_id, e.viewer_id, e.actual_reply_message_id, s.reply_text
                 FROM ai_reply.feedback_case f JOIN ai_reply.reply_episode e ON e.id = f.episode_id
                 LEFT JOIN ai_reply.episode_reply_snapshot s ON s.episode_id = f.episode_id WHERE f.id = ANY(%s)""",
            (case_ids,)).fetchall()
        for row in rows:
            text = (row["reply_text"] or "").strip()
            if len(text) >= 8 and text in instruction:
                return "actual_reply_text_in_patch"
            for identifier in (row["episode_id"], row["video_id"], row["viewer_id"], row["actual_reply_message_id"]):
                if identifier is not None and re.search(r"(?<!\d)" + re.escape(str(identifier)) + r"(?!\d)", instruction) and len(str(identifier)) >= 3:
                    return "case_identifier_in_patch"
        return None

    def candidate_version(self, proposal: dict[str, Any]) -> dict[str, Any]:
        output = proposal["proposal"]
        prompts = dict(self.baseline["prompts_json"])
        key = MODULE_PROMPT[output["module"]]
        prompts[key] = prompts[key].rstrip() + "\n\n" + output["instruction"].strip()
        row = versions.derive(self.conn, self.baseline, f"fb{self.batch['id']}", prompts=prompts,
                              note=f"feedback batch {self.batch['id']} {output['module']} patch")
        self.conn.execute("UPDATE ai_reply.batch_run SET candidate_version_id = %s WHERE id = %s", (row["version_id"], self.batch["id"]))
        return versions.load(self.conn, row["version_id"])

    # 4단계: 고정 검증
    def freeze_suite(self) -> dict[str, Any] | None:
        existing = self.conn.execute("""SELECT * FROM ai_reply.validation_run WHERE batch_run_id = %s ORDER BY id LIMIT 1""",
                                     (self.batch["id"],)).fetchone()
        if existing:
            return {"run": existing, "cases": existing["suite"]["cases"]}
        rows = self.conn.execute(
            """SELECT f.*, e.set_id, e.video_id, s.reply_text FROM ai_reply.feedback_case f
                 JOIN ai_reply.reply_episode e ON e.id = f.episode_id
                 JOIN ai_reply.episode_reply_snapshot s ON s.episode_id = f.episode_id
                WHERE f.batch_run_id < %s AND f.provenance = ANY(%s) AND f.split IN ('dev', 'regression')
                ORDER BY f.id""", (self.batch["id"], allowed_provenance(self.settings))).fetchall()
        rows = [r for r in rows if not self._protected({"id": r["episode_id"], "video_id": r["video_id"], "actual_reply_message_id": None})]
        dev_viewers = {r["viewer_id"] for r in rows if r["split"] == "dev"}
        rows = [r for r in rows if r["split"] == "dev" or r["viewer_id"] not in dev_viewers]  # 사람도 겹치지 않게
        cases = [{"case_key": f"case-{r['id']}", "case_id": r["id"], "episode_id": r["episode_id"], "split": r["split"],
                  "group": f"room:{r['room_id']}", "set_id": r["set_id"]} for r in rows]
        groups = {s: {c["group"] for c in cases if c["split"] == s} for s in ("dev", "regression")}
        if not groups["dev"] or not groups["regression"] or groups["dev"] & groups["regression"]:
            return None
        suite = {"batch_run_id": self.batch["id"], "frozen_at": iso(now_utc()), "cases": cases}
        suite_sha = sha256_json(suite)
        run = self.conn.execute(
            """INSERT INTO ai_reply.validation_run (batch_run_id, baseline_version_id, candidate_version_id, suite, suite_sha, stage)
               VALUES (%s, %s, %s, %s, %s, 'GENERATING') RETURNING *""",
            (self.batch["id"], self.baseline["version_id"], "pending", jsonb(suite), suite_sha)).fetchone()
        self.conn.execute("UPDATE ai_reply.batch_run SET frozen_suite_sha = %s WHERE id = %s", (suite_sha, self.batch["id"]))
        return {"run": run, "cases": cases}

    def regenerate(self, run_id: int, case: dict[str, Any], version: dict[str, Any]) -> dict[str, Any]:
        """사례 하나를 그 버전 프롬프트로 새 문맥에서 다시 생성·검수한다. 결과는 판정 전에 저장(동결)한다."""
        key = f"{case['case_key']}|{version['version_id']}"
        frozen = self.conn.execute("SELECT frozen_outputs -> %s AS v FROM ai_reply.validation_run WHERE id = %s", (key, run_id)).fetchone()["v"]
        if frozen is not None:
            return frozen
        episode = self.conn.execute("SELECT * FROM ai_reply.reply_episode WHERE id = %s", (case["episode_id"],)).fetchone()
        material = self.episode_material(episode)
        if material is None or material["generic_attempt"] is None:
            raise Inconclusive("validation_material_unavailable")
        from ..pipeline import _references_from_record  # 같은 검색 사례를 그대로 쓴다
        snapshot, source = material["snapshot"], material["source"]
        current = generation.current_block(source, snapshot)
        history = analysis.cited_records(list(source["history_records"] or []), snapshot["output_json"])
        references = _references_from_record(self.conn, material["generic_attempt"]["retrieval"])
        payload = generation.generation_input(f"validation-{case['case_key']}", current, "generic", references, history)
        analysis.validate_input("generation-input.schema.json", payload)
        prompts = version["prompts_json"]
        try:
            generated = self._model("generic", prompts["generic"], payload)
            kept, dropped = generation.validate_candidates(generated, payload, personal=False)
            assessments = []
            if kept:
                gate_payload = generation.gate_input(f"validation-gate-{case['case_key']}", current, "generic", references, history, [], [], kept)
                assessments = generation.validate_gate(self._model("gate", prompts["gate"], gate_payload), kept)
        except errors.JobFailure as failure:
            if failure.code == errors.BUDGET_EXCEEDED:
                raise
            raise Inconclusive("validation_generation_invalid", failure.code) from None
        # 고정 검증에는 Depth 점수가 없다: select_pool 의 점수 없음 규칙(기존 품질순 선택)과 같다.
        final = depth.select_pool(generation.accepted_candidates(kept, assessments))
        value = {"input_sha": payload["input_sha256"], "candidates": [_card_view(c, "candidate_id") for c in kept],
                 "final_cards": [{**_card_view(c, "candidate_id"), "card_id": c["candidate_id"]} for c in final],
                 "dropped": dropped}
        self.conn.execute(
            """UPDATE ai_reply.validation_run SET frozen_outputs = coalesce(frozen_outputs, '{}'::jsonb) || jsonb_build_object(%s::text, %s::jsonb),
                      updated_at = now() WHERE id = %s AND NOT (coalesce(frozen_outputs, '{}'::jsonb) ? %s)""",
            (key, jsonb(value), run_id, key))
        return self.conn.execute("SELECT frozen_outputs -> %s AS v FROM ai_reply.validation_run WHERE id = %s", (key, run_id)).fetchone()["v"]

    def validate(self, suite: dict[str, Any], candidate: dict[str, Any]) -> dict[str, Any]:
        run = suite["run"]
        self.conn.execute("UPDATE ai_reply.validation_run SET candidate_version_id = %s WHERE id = %s AND candidate_version_id = 'pending'",
                          (candidate["version_id"], run["id"]))
        if run["result"]:
            return run["metrics"]
        outputs = {}
        for case in suite["cases"]:  # 생성을 모두 끝내고 동결한 뒤에만 실제 답장을 붙인다.
            for version in (self.baseline, candidate):
                outputs[(case["case_key"], version["version_id"])] = self.regenerate(run["id"], case, version)
        frozen = self.conn.execute("SELECT frozen_outputs FROM ai_reply.validation_run WHERE id = %s", (run["id"],)).fetchone()["frozen_outputs"]
        self.conn.execute("UPDATE ai_reply.validation_run SET stage = 'FROZEN', frozen_sha = %s, updated_at = now() WHERE id = %s",
                          (sha256_json(frozen), run["id"]))
        rows = {"baseline": [], "candidate": []}
        for case in suite["cases"]:
            episode = self.conn.execute(
                """SELECT e.*, s.reply_text FROM ai_reply.reply_episode e JOIN ai_reply.episode_reply_snapshot s ON s.episode_id = e.id
                    WHERE e.id = %s""", (case["episode_id"],)).fetchone()
            material = self.episode_material(episode)
            if episode is None or material is None:
                raise Inconclusive("validation_material_unavailable")
            for label, version in (("baseline", self.baseline), ("candidate", candidate)):
                generated = outputs[(case["case_key"], version["version_id"])]
                rows[label].append(self.score(run["id"], case, material["context"], episode["reply_text"], generated))
        comparisons = []
        for old, new in zip(rows["baseline"], rows["candidate"], strict=True):
            ok = new["coverage"] >= old["coverage"] and all(new[m] <= old[m] for m in ("bad_cards", "unsupported_personalization", "card_budget_violations"))
            comparisons.append({"case_key": old["case_key"], "nonregression": ok})

        def summarize(items):
            return {s: {m: sum(r[m] for r in items if r["split"] == s) for m in ("coverage", "bad_cards", "unsupported_personalization",
                                                                              "card_budget_violations", "card_count")} for s in ("dev", "regression")}
        old_metrics, new_metrics = summarize(rows["baseline"]), summarize(rows["candidate"])
        improved = any(new_metrics[s]["coverage"] > old_metrics[s]["coverage"]
                       or any(new_metrics[s][m] < old_metrics[s][m] for m in ("bad_cards", "unsupported_personalization")) for s in old_metrics)
        budget_ok = all(r["card_budget_violations"] == 0 for r in rows["baseline"] + rows["candidate"])
        passed = all(c["nonregression"] for c in comparisons) and improved and budget_ok
        metrics = {"passed": passed, "strict_nonregression": all(c["nonregression"] for c in comparisons), "improved": improved,
                   "budget_ok": budget_ok, "baseline_metrics": old_metrics, "candidate_metrics": new_metrics,
                   "case_comparisons": comparisons, "claim": "frozen same-input comparison; QA/synthetic is functional evidence only"}
        self.conn.execute("""UPDATE ai_reply.validation_run SET stage = 'DONE', result = %s, metrics = %s, updated_at = now() WHERE id = %s""",
                          ("PASS" if passed else "FAIL", jsonb(metrics), run["id"]))
        return metrics

    def score(self, run_id: int, case: dict[str, Any], context: dict[str, Any], reply_text: str, generated: dict[str, Any]) -> dict[str, Any]:
        reply = {"text": reply_text}
        quality = self.judge("actual_appropriateness", {"context": context, "actual_reply": reply}, validation_run_id=run_id, case_key=case["case_key"])
        if quality["verdict"] != "appropriate" or quality["confidence"] != "high" or not quality["stable_meaning"]:
            raise Inconclusive("heldout_actual_unsuitable_or_uncertain")
        try:
            direction = self.judge("direction_match", {"context": context, "actual_reply": reply, "candidates": generated["candidates"],
                                                       "final_cards": self._shuffled(generated["final_cards"], case["case_key"])},
                                   validation_run_id=run_id, case_key=case["case_key"])
        except errors.JobFailure as failure:
            if failure.code == errors.BUDGET_EXCEEDED:
                raise
            raise Inconclusive("validation_direction_invalid", failure.code) from None
        if direction["raw"] == "uncertain" or direction["final"] == "uncertain":
            raise Inconclusive("validation_direction_uncertain")
        bad = unsupported = 0
        for card in generated["final_cards"]:
            verdict = self.judge("card_quality", {"context": context, "card": {k: card[k] for k in ("category", "title", "example")}},
                                 validation_run_id=run_id, case_key=case["case_key"])
            if verdict["verdict"] == "uncertain" or verdict["confidence"] != "high" or not verdict["stable_meaning"]:
                raise Inconclusive("validation_card_quality_uncertain")
            bad += verdict["verdict"] == "inappropriate"
            unsupported += bool(verdict["unsupported_personalization"])
        categories = [c["category"] for c in generated["final_cards"]]
        violations = int(len(categories) > 3 or len(set(categories)) != len(categories))
        return {"case_key": case["case_key"], "split": case["split"], "coverage": int(direction["final"] == "included"),
                "bad_cards": bad, "unsupported_personalization": unsupported, "card_budget_violations": violations,
                "card_count": len(categories)}

    def activate_or_hold(self, candidate: dict[str, Any], validation: dict[str, Any], consumed: list[int]) -> dict[str, Any]:
        self.conn.execute("UPDATE ai_reply.version_registry SET status = 'VALIDATED', validation_id = %s WHERE version_id = %s",
                          (self.batch["id"], candidate["version_id"]))
        result = {"decision": "validated", "validation": validation, "candidate_version_id": candidate["version_id"]}
        if not self.settings.automatic_activation_enabled:
            return self.finish("VALIDATED", {**result, "activation": "disabled"}, consumed)
        try:
            revision = versions.activate(self.conn, candidate["version_id"], self.batch["baseline_version_id"],
                                         self.batch["baseline_revision"], f"feedback batch {self.batch['id']}", self.batch["id"])
        except versions.CasConflict:
            # 배치 중 활성 버전이 바뀌었으면 덮어쓰지 않고 보류한다(FB05).
            return self.finish("VALIDATED", {**result, "activation": "cas_conflict"}, consumed)
        return self.finish("ACTIVE", {**result, "activation": "activated", "revision": revision}, consumed)


def context_of(snapshot: dict[str, Any], source: dict[str, Any] | None, viewer_id: Any) -> dict[str, Any]:
    history = analysis.cited_records(list((source or {}).get("history_records") or []), snapshot["output_json"])
    return {"video_id": str(snapshot["video_id"]), "viewer_id": str(viewer_id),
            "author_id": str((source or {}).get("author_id", "")), "published_at": iso(snapshot["cutoff_at"]),
            "caption": (source or {}).get("caption", ""), "contextual_description": snapshot["contextual_description"],
            "history_records": history}


def split_of(room_id: Any) -> str:
    """방 단위로 dev/regression 을 나눈다(같은 방은 한쪽에만)."""
    return "regression" if int(sha256_text(f"room:{room_id}")[:8], 16) % 3 == 0 else "dev"


def _card_view(card: dict[str, Any], id_key: str) -> dict[str, Any]:
    return {"candidate_id": card[id_key], "category": card["category"], "title": card["title"], "example": card["example"]}


def _public(aggregates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """개선안 모델에는 원인·개수만 준다(사례 id·원문 없음)."""
    return [{"reason": a["reason"], "count": a["count"]} for a in aggregates]

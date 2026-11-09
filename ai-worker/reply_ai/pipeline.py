"""온라인 작업(ANALYSIS · GENERIC · PERSONAL · PURGE_VIDEO).

작업 하나 = 한 단계 묶음. 모델 단계의 유효 결과는 ai_job.result.stages 에 먼저 남기고(재개 때 다시 부르지 않는다),
마지막에 lease 를 확인한 같은 트랜잭션에서 결과 행과 작업 완료를 함께 쓴다.
로그에는 id·해시·토큰·비용만 남긴다(원문·캡션·프레임·서명 URL 없음).
"""
from __future__ import annotations

import logging
import time
import uuid
from dataclasses import dataclass, field
from datetime import timedelta
from typing import Any, Callable

import numpy as np

from . import analysis, budget, errors, generation, jobs, media, tokens, versions
from .config import Settings
from .db import jsonb
from .embedding import Embedder, version_of
from .gallery import ensure_embeddings, load_gallery
from .provider import Image, ModelRequest, ModelResult, Provider
from .retrieval import Current, Protected, Retrieved, query_text, retrieve
from .util import canonical, now_utc, sha256_json

log = logging.getLogger("reply_ai.pipeline")


@dataclass
class JobContext:
    conn: Any
    job: jobs.Job
    owner: str
    settings: Settings
    provider: Provider
    embedder_factory: Callable[[], Embedder]
    fetcher: media.MediaFetcher
    spent: list[dict[str, Any]] = field(default_factory=list)

    def stage(self, key: str) -> Any:
        return self.job.stages().get(key)

    def save_stage(self, key: str, value: Any) -> None:
        jobs.save_stage(self.conn, self.job.id, self.owner, key, value)
        self.job.result.setdefault("stages", {})[key] = value

    def model_call(self, key: str, request: ModelRequest, *, batch_run_id: int | None = None) -> ModelResult:
        cached = self.stage(key)
        if cached is not None:
            result = ModelResult.from_record(cached)
            self.spent.append({"stage": key, "cost_usd": 0.0, "cached": True, **result.usage})
            return result
        estimate_tokens = tokens.estimate_input(request.instructions, canonical(request.payload),
                                                [(_jpeg_size(i.jpeg)) + (i.detail,) for i in request.images])
        amount = budget.estimate_usd(self.settings, estimate_tokens, request.max_output_tokens)
        reservation = (budget.reserve_batch(self.conn, batch_run_id, amount) if batch_run_id is not None
                       else budget.reserve_online(self.conn, self.settings, amount))
        started = time.monotonic()
        try:
            result = self.provider.call(request)
        except errors.JobFailure as failure:
            budget.settle(self.conn, reservation, failure.cost_usd)
            log.warning("model_call_failed", extra={"job_id": self.job.id, "stage": key, "failure_code": failure.code,
                                                    "provider_request_id": failure.provider_request_id})
            raise
        except Exception:
            budget.settle(self.conn, reservation, 0.0)
            raise
        budget.settle(self.conn, reservation, result.cost_usd)
        record = result.record()
        self.save_stage(key, record)
        self.spent.append({"stage": key, "cost_usd": round(result.cost_usd, 6), **result.usage})
        log.info("model_call", extra={"job_id": self.job.id, "stage": key, "provider_request_id": result.provider_request_id,
                                      "input_tokens": result.usage.get("input_tokens"), "output_tokens": result.usage.get("output_tokens"),
                                      "cost_usd": round(result.cost_usd, 6), "ms": int((time.monotonic() - started) * 1000)})
        return result

    def usage_summary(self) -> dict[str, Any]:
        return {"calls": self.spent, "cost_usd": round(sum(c["cost_usd"] for c in self.spent), 6)}


def _jpeg_size(data: bytes) -> tuple[int, int]:
    """JPEG SOF 마커에서 크기를 읽는다(토큰 추정용)."""
    i = 2
    while i + 9 < len(data):
        if data[i] != 0xFF:
            i += 1
            continue
        marker = data[i + 1]
        length = int.from_bytes(data[i + 2:i + 4], "big")
        if marker in (0xC0, 0xC1, 0xC2):
            return int.from_bytes(data[i + 7:i + 9], "big"), int.from_bytes(data[i + 5:i + 7], "big")
        i += 2 + length
    return 768, 768


# ───────────────────────── ANALYSIS ─────────────────────────

def run_analysis(ctx: JobContext) -> None:
    conn = ctx.conn
    input_id = int(ctx.job.payload["analysis_input_id"])
    row = conn.execute(
        """SELECT ai.*, ar.status AS request_status FROM ai_reply.analysis_input ai
             JOIN ai_reply.analysis_request ar ON ar.id = ai.analysis_request_id WHERE ai.id = %s""", (input_id,)).fetchone()
    if row is None:
        raise errors.JobFailure(errors.INPUT_MISSING, f"analysis_input {input_id}")
    if row["request_status"] == "CANCELLED":
        jobs.cancel(conn, ctx.job.id, ctx.owner, errors.INPUT_REVOKED, "analysis request cancelled")
        return
    if row["purged_at"] is not None:
        raise errors.JobFailure(errors.INPUT_MISSING, "analysis input purged")
    existing = conn.execute("SELECT id, status FROM ai_reply.analysis_snapshot WHERE analysis_input_id = %s", (input_id,)).fetchone()
    if existing:
        _finish_analysis(ctx, existing["id"], existing["status"], reused=True)
        return

    version = versions.for_preprocess(conn, row["preprocess_version"])
    prompt = version["prompts_json"]["analysis"]
    meta = ctx.stage("media")
    images: tuple[Image, ...] = ()
    if ctx.stage("analysis") is None:
        data, source, object_version = ctx.fetcher.fetch(row["media_url"])
        extracted = media.extract_frames(ctx.settings, data, source, object_version)
        frames = [f.meta() for f in extracted.frames]
        images = tuple(Image(f.jpeg) for f in extracted.frames)
        full = analysis.model_input(row, frames, include_history=True)
        analysis.validate_input("analysis-input.schema.json", full)
        estimate = tokens.estimate_input(prompt, canonical(full), [(f["width"], f["height"], "high") for f in frames])
        include_history = estimate <= ctx.settings.input_token_budget
        status = row["history_status"] if include_history else "OVER_BUDGET"
        meta = {"media": extracted.record(), "include_history": include_history, "history_input_status": status,
                "estimated_input_tokens": estimate, "version_id": version["version_id"]}
        ctx.save_stage("media", meta)
    frames = [{k: f[k] for k in ("frame_id", "position_percent", "timestamp_ms", "width", "height", "sha256")}
              for f in meta["media"]["frames"]]
    payload = analysis.model_input(row, frames, include_history=meta["include_history"])
    analysis.validate_input("analysis-input.schema.json", payload)
    result = ctx.model_call("analysis", ModelRequest("analysis", prompt, payload, images,
                                                     ctx.settings.max_output_tokens("analysis")))
    output = result.output
    cited = analysis.validate_output(output, payload)

    with conn.transaction():
        jobs.lock_owned(conn, ctx.job.id, ctx.owner)
        inserted = conn.execute(
            """INSERT INTO ai_reply.analysis_snapshot (analysis_input_id, analysis_key, video_id, scope_hash, caption_revision,
                   media_revision, history_snapshot_hash, preprocess_version, version_id, cutoff_at, input_sha, output_sha,
                   media_sha, frames, output_json, current_visual_description, contextual_description, history_input_status,
                   history_requirement, history_used, model, provider_request_id, usage, cost_usd, status)
               VALUES (%(input_id)s, %(key)s, %(video_id)s, %(scope)s, %(caption)s, %(media)s, %(history)s, %(pre)s, %(version)s,
                   %(cutoff)s, %(input_sha)s, %(output_sha)s, %(media_sha)s, %(frames)s, %(output)s, %(current)s, %(contextual)s,
                   %(status)s, %(requirement)s, %(used)s, %(model)s, %(request_id)s, %(usage)s, %(cost)s, 'READY')
               ON CONFLICT DO NOTHING RETURNING id""",
            {"input_id": input_id, "key": analysis.analysis_key(row), "video_id": row["video_id"], "scope": row["scope_hash"],
             "caption": row["caption_revision"], "media": row["media_revision"], "history": row["history_snapshot_hash"],
             "pre": row["preprocess_version"], "version": meta.get("version_id") or version["version_id"],
             "cutoff": row["published_at"], "input_sha": payload["input_sha256"],
             "output_sha": sha256_json(output), "media_sha": meta["media"]["media_sha"],
             "frames": jsonb(_frames_doc(meta["media"])),
             "output": jsonb(output), "current": output["current_visual_description"],
             "contextual": output["contextual_description"], "status": meta["history_input_status"],
             "requirement": output["history_requirement"], "used": output["history_used"], "model": result.model,
             "request_id": result.provider_request_id, "usage": jsonb(result.usage), "cost": result.cost_usd}).fetchone()
        if inserted is None:
            other = conn.execute(
                """SELECT id, status FROM ai_reply.analysis_snapshot WHERE analysis_input_id = %s OR analysis_key = %s
                    ORDER BY (analysis_input_id = %s) DESC LIMIT 1""",
                (input_id, analysis.analysis_key(row), input_id)).fetchone()
            snapshot_id, status = other["id"], other["status"]
        else:
            snapshot_id, status = inserted["id"], "READY"
        if cited:
            conn.execute(
                """UPDATE ai_reply.evidence_source SET cited = true
                    WHERE owner_kind = 'ANALYSIS' AND owner_id = %s AND source_id = ANY(%s)""", (input_id, sorted(cited)))
        _finish_analysis(ctx, snapshot_id, status, reused=inserted is None, in_transaction=True)


def _frames_doc(record: dict[str, Any]) -> list[dict[str, Any]]:
    """프레임마다 실제 해상도·시각·해시와 추출 도구·원본 객체 버전을 함께 남긴다."""
    common = {k: record[k] for k in ("source", "object_version", "duration_ms", "rotation", "tool_version")}
    return [{**frame, **common} for frame in record["frames"]]


def _finish_analysis(ctx: JobContext, snapshot_id: int, status: str, *, reused: bool, in_transaction: bool = False) -> None:
    conn = ctx.conn

    def body():
        if not in_transaction:
            jobs.lock_owned(conn, ctx.job.id, ctx.owner)
        if status != "READY":
            jobs.cancel(conn, ctx.job.id, ctx.owner, errors.INPUT_REVOKED, "analysis snapshot revoked")
            return
        enqueue_generic(conn, snapshot_id, priority=ctx.job.priority)
        jobs.succeed(conn, ctx.job.id, ctx.owner, {"analysis_id": snapshot_id, "reused": reused, **ctx.usage_summary()})

    if in_transaction:
        body()
    else:
        with conn.transaction():
            body()


def generic_key(analysis_id: int, version_id: str, gallery_snapshot_id: str, revision_no: int) -> str:
    return f"generic:{analysis_id}:{version_id}:{gallery_snapshot_id}:r{revision_no}"


def enqueue_generic(conn, analysis_id: int, priority: int = 0, revision_no: int = 1) -> str | None:
    current = versions.active(conn)
    if current is None:
        return None
    key = generic_key(analysis_id, current["version_id"], current["gallery_snapshot_id"], revision_no)
    jobs.enqueue(conn, key, "GENERIC", {"analysis_id": analysis_id, "version_id": current["version_id"],
                                       "gallery_snapshot_id": current["gallery_snapshot_id"], "revision_no": revision_no},
                 priority=priority)
    return key


# ───────────────────────── GENERIC ─────────────────────────

def _analysis_bundle(conn, analysis_id: int) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
    snapshot = conn.execute("SELECT * FROM ai_reply.analysis_snapshot WHERE id = %s", (analysis_id,)).fetchone()
    if snapshot is None:
        return None, None
    source = conn.execute("SELECT * FROM ai_reply.analysis_input WHERE id = %s", (snapshot["analysis_input_id"],)).fetchone()
    return snapshot, source


def _existing_attempt(conn, key: str) -> dict[str, Any] | None:
    return conn.execute("""SELECT id, result_set_id FROM ai_reply.generation_attempt
                            WHERE attempt_key = %s AND status = 'SUCCEEDED'""", (key,)).fetchone()


def _retrieval(ctx: JobContext, snapshot: dict[str, Any], source: dict[str, Any], version: dict[str, Any],
               gallery_snapshot_id: str) -> dict[str, Any]:
    cached = ctx.stage("retrieval")
    if cached is not None:
        return cached
    conn = ctx.conn
    embedding_config = version["config_json"]["embedding"]
    query = query_text(embedding_config["query_instruction"], source["caption"], snapshot["current_visual_description"])
    refs, pairs = load_gallery(conn, gallery_snapshot_id)
    protected = Protected.from_rows(conn.execute("SELECT kind, value FROM ai_reply.protected_registry").fetchall())
    frames = snapshot["frames"] or []
    current = Current(video_id=snapshot["video_id"], room_id=source["room_id"], published_at=source["published_at"],
                      media_sha=snapshot.get("media_sha"), frame_shas={f["sha256"] for f in frames or []})
    vectors: dict[int, np.ndarray] = {}
    query_vector = None
    embedding_version = None
    if refs:
        embedder = ctx.embedder_factory()
        embedding_version = version_of(embedder.info())
        if embedding_version["dimensions"] != int(embedding_config["dimensions"]):
            raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "embedding dimensions differ from version config")
        # 버전이 임베딩 제공자·모델·요청 방식을 고정했으면(운영 배포 이후 버전) 작업기 설정과 같아야 한다.
        pinned = {k: embedding_config[k] for k in ("provider", "model_id", "request_mode") if "provider" in embedding_config}
        if pinned and any(embedding_version[k] != v for k, v in pinned.items()):
            raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "worker embedder differs from version config")
        vectors = ensure_embeddings(conn, embedder, [r["id"] for r in refs])
        query_vector = embedder.embed([query])[0]
    found: Retrieved = retrieve(current, refs, pairs, vectors, query_vector, protected, query=query,
                                embedding_version=embedding_version, gallery_snapshot_id=gallery_snapshot_id,
                                top_k=int(embedding_config["top_k"]), max_replies=int(embedding_config["max_replies_per_video"]))
    value = {"references": found.references, "record": found.record}
    ctx.save_stage("retrieval", value)
    return value


def run_generic(ctx: JobContext) -> None:
    conn = ctx.conn
    payload = ctx.job.payload
    analysis_id, revision_no = int(payload["analysis_id"]), int(payload.get("revision_no", 1))
    if (existing := _existing_attempt(conn, ctx.job.job_key)) is not None:
        with conn.transaction():
            jobs.lock_owned(conn, ctx.job.id, ctx.owner)
            jobs.succeed(conn, ctx.job.id, ctx.owner, {"attempt_id": existing["id"], "set_id": existing["result_set_id"], "reused": True})
        return
    snapshot, source = _analysis_bundle(conn, analysis_id)
    if snapshot is None or source is None or snapshot["status"] != "READY":
        jobs.cancel(conn, ctx.job.id, ctx.owner, errors.INPUT_REVOKED, "analysis not ready")
        return
    version = versions.load(conn, payload["version_id"])
    prompts = version["prompts_json"]
    found = _retrieval(ctx, snapshot, source, version, payload["gallery_snapshot_id"])
    current = generation.current_block(source, snapshot)
    history = analysis.cited_records(list(source["history_records"] or []), snapshot["output_json"])
    gen_payload = generation.generation_input(f"generic-{analysis_id}-r{revision_no}", current, "generic",
                                              found["references"], history)
    analysis.validate_input("generation-input.schema.json", gen_payload)
    generated = ctx.model_call("generic", ModelRequest("generic", prompts["generic"], gen_payload,
                                                       max_output_tokens=ctx.settings.max_output_tokens("generic")))
    kept, dropped = generation.validate_candidates(generated.output, gen_payload, personal=False)
    gate_output, assessments, gate_sha = None, [], None
    if kept:
        gate_payload = generation.gate_input(f"gate-generic-{analysis_id}-r{revision_no}", current, "generic",
                                             found["references"], history, [], [], kept)
        analysis.validate_input("gate-input.schema.json", gate_payload)
        gate_sha = gate_payload["input_sha256"]
        gated = ctx.model_call("gate", ModelRequest("gate", prompts["gate"], gate_payload,
                                                    max_output_tokens=ctx.settings.max_output_tokens("gate")))
        gate_output = gated.output
        assessments = generation.validate_gate(gate_output, kept)
    selected = generation.select_generic(generation.accepted_candidates(kept, assessments))
    internal = [generation.new_card(c) for c in selected]
    with conn.transaction():
        jobs.lock_owned(conn, ctx.job.id, ctx.owner)
        attempt_id = _insert_attempt(conn, ctx, stage="GENERIC", analysis=snapshot, version=version,
                                     gallery=payload["gallery_snapshot_id"], revision_no=revision_no,
                                     retrieval=found["record"], gen_sha=gen_payload["input_sha256"], gate_sha=gate_sha,
                                     candidates={"output": generated.output, "kept": kept, "dropped": dropped},
                                     gate=gate_output)
        set_id = _insert_set(conn, ctx, attempt_id=attempt_id, kind="GENERIC", analysis=snapshot, version=version,
                             gallery=payload["gallery_snapshot_id"], revision_no=revision_no, internal=internal,
                             expires_at=now_utc() + timedelta(hours=ctx.settings.set_ttl_hours))
        conn.execute("UPDATE ai_reply.generation_attempt SET result_set_id = %s WHERE id = %s", (set_id, attempt_id))
        jobs.succeed(conn, ctx.job.id, ctx.owner, {"attempt_id": attempt_id, "set_id": set_id,
                                                   "status": "READY" if internal else "EMPTY", **ctx.usage_summary()})


def _insert_attempt(conn, ctx: JobContext, *, stage: str, analysis: dict[str, Any], version: dict[str, Any], gallery: str,
                    revision_no: int, retrieval: dict[str, Any] | None, gen_sha: str | None, gate_sha: str | None,
                    candidates: dict[str, Any] | None, gate: dict[str, Any] | None, viewer_id: int | None = None,
                    personal_input_id: int | None = None, base_set_id: str | None = None) -> int:
    summary = ctx.usage_summary()
    ids = [ctx.stage(k).get("provider_request_id") for k in ("generic", "gate", "personal", "personal_gate") if ctx.stage(k)]
    row = conn.execute(
        """INSERT INTO ai_reply.generation_attempt (attempt_key, stage, analysis_id, video_id, viewer_id, personal_input_id,
               base_set_id, revision_no, version_id, gallery_snapshot_id, retrieval, generation_input_sha, gate_input_sha,
               candidates, gate, status, usage, cost_usd, model, provider_request_ids)
           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'SUCCEEDED', %s, %s, %s, %s) RETURNING id""",
        (ctx.job.job_key, stage, analysis["id"], analysis["video_id"], viewer_id, personal_input_id, base_set_id, revision_no,
         version["version_id"], gallery, jsonb(retrieval) if retrieval is not None else None, gen_sha, gate_sha,
         jsonb(candidates) if candidates is not None else None, jsonb(gate) if gate is not None else None,
         jsonb(summary), summary["cost_usd"], ctx.provider.model, jsonb(ids))).fetchone()
    return row["id"]


def _insert_set(conn, ctx: JobContext, *, attempt_id: int, kind: str, analysis: dict[str, Any], version: dict[str, Any],
                gallery: str, revision_no: int, internal: list[dict[str, Any]], expires_at, viewer_id: int | None = None,
                base_set_id: str | None = None) -> str:
    set_id = str(uuid.uuid4())
    conn.execute(
        """INSERT INTO ai_reply.recommendation_set (set_id, attempt_id, kind, base_set_id, viewer_id, analysis_id, video_id,
               revision_no, version_id, gallery_snapshot_id, status, cards, internal_cards, expires_at)
           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        (set_id, attempt_id, kind, base_set_id, viewer_id, analysis["id"], analysis["video_id"], revision_no,
         version["version_id"], gallery, "READY" if internal else "EMPTY", jsonb(generation.public_cards(internal)),
         jsonb(internal), expires_at))
    return set_id


# ───────────────────────── PERSONAL ─────────────────────────

def _references_from_record(conn, record: dict[str, Any] | None) -> list[dict[str, Any]]:
    """일반 시도의 검색 기록으로 같은 참고 자료를 다시 만든다(개인화도 같은 사례를 본다)."""
    if not record or not record.get("ranked"):
        return []
    by_alias = {r["alias"]: r for r in record["ranked"]}
    replies_by_ref: dict[str, list[dict[str, Any]]] = {}
    pair_ids = [s["reference_pair_id"] for s in record.get("selected_replies", [])]
    pairs = {p["id"]: p for p in conn.execute("SELECT * FROM ai_reply.reference_pair WHERE id = ANY(%s)", (pair_ids,)).fetchall()}
    for selected in record.get("selected_replies", []):
        alias = selected["alias"].split("-", 1)[0]
        pair = pairs.get(selected["reference_pair_id"])
        if pair and pair["deleted_at"] is None:
            replies_by_ref.setdefault(alias, []).append({"reply_id": selected["alias"], "text": pair["sanitized_text"]})
    ids = [r["reference_video_id"] for r in record["ranked"]]
    refs = {r["id"]: r for r in conn.execute("SELECT * FROM ai_reply.reference_video WHERE id = ANY(%s)", (ids,)).fetchall()}
    result = []
    for alias, ranked in by_alias.items():
        ref = refs.get(ranked["reference_video_id"])
        if ref is None or ref["deleted_at"] is not None or not replies_by_ref.get(alias):
            continue
        result.append({"reference_id": alias, "caption": ref["caption"], "current_visual_description": ref["current_visual_description"],
                       "replies": replies_by_ref[alias]})
    return result


def run_personal(ctx: JobContext) -> None:
    conn = ctx.conn
    personal_id = int(ctx.job.payload["personal_input_id"])
    if (existing := _existing_attempt(conn, ctx.job.job_key)) is not None:
        with conn.transaction():
            jobs.lock_owned(conn, ctx.job.id, ctx.owner)
            jobs.succeed(conn, ctx.job.id, ctx.owner, {"attempt_id": existing["id"], "set_id": existing["result_set_id"], "reused": True})
        return
    pinput = conn.execute("SELECT * FROM ai_reply.personal_input WHERE id = %s", (personal_id,)).fetchone()
    if pinput is None or pinput["purged_at"] is not None:
        raise errors.JobFailure(errors.INPUT_MISSING, f"personal_input {personal_id}")
    base = conn.execute("SELECT * FROM ai_reply.recommendation_set WHERE set_id = %s", (pinput["base_set_id"],)).fetchone()
    snapshot, source = _analysis_bundle(conn, pinput["analysis_id"])
    if base is None or base["revoked_at"] is not None or snapshot is None or snapshot["status"] != "READY" or source is None:
        jobs.cancel(conn, ctx.job.id, ctx.owner, errors.INPUT_REVOKED, "base set or analysis not valid")
        return
    version = versions.load(conn, pinput["version_id"])
    prompts = version["prompts_json"]
    viewer, uploader = str(pinput["viewer_id"]), str(source["author_id"])
    records = list(pinput["personal_records"] or [])
    base_internal = list(base["internal_cards"] or [])
    result_set_id, candidates_doc, gate_output, gen_sha, gate_sha = base["set_id"], None, None, None, None
    base_attempt = conn.execute("SELECT retrieval FROM ai_reply.generation_attempt WHERE id = %s", (base["attempt_id"],)).fetchone()
    references = _references_from_record(conn, base_attempt and base_attempt["retrieval"])
    chosen: list[dict[str, Any]] = []
    if records:  # 근거가 없으면 모델을 부르지 않고 일반 카드를 그대로 쓴다.
        current = generation.current_block(source, snapshot)
        history = analysis.cited_records(list(source["history_records"] or []), snapshot["output_json"])
        base_cards = generation.base_cards_for_model(base_internal)
        gen_payload = generation.generation_input(f"personal-{personal_id}", current, viewer, references, history,
                                                  records, base_cards)
        analysis.validate_input("generation-input.schema.json", gen_payload)
        gen_sha = gen_payload["input_sha256"]
        generated = ctx.model_call("personal", ModelRequest("personal", prompts["personal"], gen_payload,
                                                            max_output_tokens=ctx.settings.max_output_tokens("personal")))
        kept, dropped = generation.validate_candidates(generated.output, gen_payload, personal=True,
                                                       viewer_id=viewer, uploader_id=uploader)
        candidates_doc = {"output": generated.output, "kept": kept, "dropped": dropped}
        if kept:
            used_ids = {q["source_id"] for c in kept for q in c["personalization"]["source_quotes"]}
            gate_payload = generation.gate_input(f"gate-personal-{personal_id}", current, viewer, references, history,
                                                 [r for r in records if r["source_id"] in used_ids], base_cards, kept)
            analysis.validate_input("gate-input.schema.json", gate_payload)
            gate_sha = gate_payload["input_sha256"]
            gated = ctx.model_call("personal_gate", ModelRequest("gate", prompts["gate"], gate_payload,
                                                                 max_output_tokens=ctx.settings.max_output_tokens("gate")))
            gate_output = gated.output
            assessments = generation.validate_gate(gate_output, kept)
            accepted = generation.accepted_candidates(kept, assessments, personal=True)
            base_for_merge = [{**c, "redundancy_group": "base:" + c["card_id"]} for c in base_internal]
            chosen, used = generation.merge_personal(base_for_merge, accepted)
            if not used:
                chosen = []
    with conn.transaction():
        jobs.lock_owned(conn, ctx.job.id, ctx.owner)
        attempt_id = _insert_attempt(conn, ctx, stage="PERSONAL", analysis=snapshot, version=version,
                                     gallery=base["gallery_snapshot_id"], revision_no=base["revision_no"], retrieval=None,
                                     gen_sha=gen_sha, gate_sha=gate_sha, candidates=candidates_doc, gate=gate_output,
                                     viewer_id=pinput["viewer_id"], personal_input_id=personal_id, base_set_id=base["set_id"])
        if chosen:
            internal = [c if "card_id" in c else generation.new_card(c) for c in chosen]
            for card in internal:  # 일반 카드는 원래 의미 그룹으로 되돌려 저장한다(base: 표시는 병합용).
                if card["redundancy_group"].startswith("base:"):
                    original = next(b for b in base_internal if b["card_id"] == card["card_id"])
                    card["redundancy_group"] = original["redundancy_group"]
            result_set_id = _insert_set(conn, ctx, attempt_id=attempt_id, kind="PERSONAL", analysis=snapshot, version=version,
                                        gallery=base["gallery_snapshot_id"], revision_no=base["revision_no"], internal=internal,
                                        expires_at=base["expires_at"], viewer_id=pinput["viewer_id"], base_set_id=base["set_id"])
        conn.execute("UPDATE ai_reply.generation_attempt SET result_set_id = %s WHERE id = %s", (result_set_id, attempt_id))
        jobs.succeed(conn, ctx.job.id, ctx.owner, {"attempt_id": attempt_id, "set_id": result_set_id,
                                                   "personalized": result_set_id != base["set_id"], **ctx.usage_summary()})


# ───────────────────────── PURGE_VIDEO ─────────────────────────

def run_purge(ctx: JobContext) -> None:
    """삭제·숨김된 영상의 파생 자료를 막고 지운다: 분석·세트 무효화, 그 영상을 근거로 쓴 다른 분석 무효화,
    그 영상에서 나온 참고 사례·벡터 삭제, 대기 중 작업 취소."""
    conn = ctx.conn
    video_id = int(ctx.job.payload["video_id"])
    reason = str(ctx.job.payload.get("reason") or "VIDEO_REMOVED")[:60]
    with conn.transaction():
        jobs.lock_owned(conn, ctx.job.id, ctx.owner)
        dependent = [r["owner_id"] for r in conn.execute(
            """SELECT DISTINCT owner_id FROM ai_reply.evidence_source
                WHERE owner_kind = 'ANALYSIS' AND source_kind = 'video_description' AND source_ref_id = %s""", (video_id,)).fetchall()]
        conn.execute("""UPDATE ai_reply.evidence_source SET deleted_at = coalesce(deleted_at, now())
                         WHERE source_kind = 'video_description' AND source_ref_id = %s""", (video_id,))
        revoked = conn.execute(
            """UPDATE ai_reply.analysis_snapshot SET status = 'REVOKED', revoked_at = now(), revoke_reason = %s
                WHERE status = 'READY' AND (video_id = %s OR analysis_input_id = ANY(%s)) RETURNING id""",
            (reason, video_id, dependent)).fetchall()
        analysis_ids = [r["id"] for r in revoked]
        sets = conn.execute(
            """UPDATE ai_reply.recommendation_set SET revoked_at = now(), revoke_reason = %s
                WHERE revoked_at IS NULL AND (video_id = %s OR analysis_id = ANY(%s)) RETURNING set_id""",
            (reason, video_id, analysis_ids)).fetchall()
        refs = [r["id"] for r in conn.execute(
            """UPDATE ai_reply.reference_video SET deleted_at = coalesce(deleted_at, now())
                WHERE source_video_id = %s RETURNING id""", (video_id,)).fetchall()]
        conn.execute("UPDATE ai_reply.reference_pair SET deleted_at = coalesce(deleted_at, now()) WHERE reference_video_id = ANY(%s)", (refs,))
        conn.execute("DELETE FROM ai_reply.embedding WHERE reference_video_id = ANY(%s)", (refs,))
        cancelled = conn.execute(
            """UPDATE ai_reply.ai_job SET status = 'CANCELLED', failure_code = %s, finished_at = now(), updated_at = now()
                WHERE status IN ('QUEUED', 'RETRY_WAIT') AND id <> %s AND (
                      (job_type = 'ANALYSIS' AND (payload ->> 'analysis_input_id')::bigint IN
                           (SELECT id FROM ai_reply.analysis_input WHERE video_id = %s))
                   OR (job_type = 'GENERIC' AND (payload ->> 'analysis_id')::bigint = ANY(%s)))
               RETURNING id""",
            (errors.INPUT_REVOKED, ctx.job.id, video_id, analysis_ids)).fetchall()
        jobs.succeed(conn, ctx.job.id, ctx.owner, {"revoked_analyses": len(analysis_ids), "revoked_sets": len(sets),
                                                   "deleted_references": len(refs), "cancelled_jobs": len(cancelled)})


HANDLERS: dict[str, Callable[[JobContext], None]] = {
    "ANALYSIS": run_analysis,
    "GENERIC": run_generic,
    "PERSONAL": run_personal,
    "PURGE_VIDEO": run_purge,
}

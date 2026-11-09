"""온라인 작업 흐름(분석 → 일반 → 개인화 → 무효화). 가짜 모델·임베더·미디어로 PostgreSQL 에서 끝까지 돈다."""
import uuid

import pytest

from reply_ai import errors, gallery, jobs, versions
from reply_ai.config import Settings
from reply_ai.db import connect, jsonb
from reply_ai.embedding import HashEmbedder
from reply_ai.media import MediaFetcher
from reply_ai.provider import ScriptedProvider
from reply_ai.resources import GATE_BOOLEANS
from reply_ai.util import sha256_text
from reply_ai.worker import Worker

from .conftest import T0, PinnedFakeEmbedder, record, seed_analysis_input

pytestmark = pytest.mark.pg


class FakeFetcher:
    def __init__(self, video: bytes):
        self.video = video
        self.calls = 0

    def fetch(self, url):
        self.calls += 1
        if not url.startswith("http://media.test/"):
            raise errors.JobFailure(errors.MEDIA_SOURCE_NOT_ALLOWED, "test")
        return self.video, "http", "etag-1"


HISTORY = [record("chat:1", "11", "내일까지 과제 내야 해서 밤샐 듯", minutes_before=600),
           record("chat:2", "3", "화이팅!! 무슨 과제예요?", minutes_before=590, parents=["chat:1"])]
DESCRIPTION = "책상 위 노트북 화면에 문서가 열려 있고 옆에 커피잔이 있다. 캡션은 과제 마감이 하루 남았다는 뜻이다."
CONTEXTUAL = DESCRIPTION + " 전날 방에서 과제 때문에 밤샐 것 같다고 말한 흐름이 이어진다."


def analysis_output(linked=True):
    links = [{"link_id": "L1", "kind": "conversational_continuation", "statement": "전날 과제 이야기가 이어진다",
              "description_span": "전날 방에서 과제 때문에 밤샐 것 같다고 말한 흐름이 이어진다.",
              "source_quotes": [{"source_id": "chat:1", "quote": "과제 내야 해서 밤샐 듯"}], "current_anchor": "과제 마감",
              "strength": "explicit"}] if linked else []
    return {"current_visual_description": DESCRIPTION, "contextual_description": CONTEXTUAL if linked else DESCRIPTION,
            "history_requirement": "helpful" if linked else "not_needed", "history_used": linked, "history_note": "",
            "visual_facts": [{"fact_id": "F1", "text": "노트북과 커피잔", "frame_ids": ["frame-10", "frame-50"]}],
            "context_links": links}


def candidates_output(n=3):
    rows = [("question", "무슨 과제인지 물어볼까요?", "무슨 과제예요?", "과제 마감"),
            ("support", "마감까지 응원해 볼까요?", "마감 화이팅이에요!", "과제 마감"),
            ("empathy", "밤새는 피곤함에 공감해 볼까요?", "밤새면 너무 피곤하죠", "밤샐 것 같다"),
            ("humor", "커피 몇 잔째인지 물어볼까요?", "커피 몇 잔째예요?", "커피잔")]
    return {"candidates": [{"candidate_id": f"G{i}", "category": c, "title": t, "example": e, "current_anchor": a,
                            "reference_ids": [], "personalization": {"kind": "none", "source_quotes": [], "actor_ids": [], "delta": ""}}
                           for i, (c, t, e, a) in enumerate(rows[:n], 1)], "personalization_used": False, "reason": "일반 후보"}


def gate_output(ids, quality=3, ok=True, useful=False, groups=None):
    return {"assessments": [{"candidate_id": cid, **{k: ok for k in GATE_BOOLEANS}, "useful_personalization": useful,
                             "quality": quality, "redundancy_group": (groups or {}).get(cid, "grp-" + cid), "issue": "none",
                             "reason": "근거가 있다"} for cid in ids]}


def worker(database_url, provider, fetcher, embedder=None, **overrides):
    overrides_embedder = embedder
    settings = Settings(database_url=database_url, **overrides)
    return Worker(settings, provider=provider, embedder_factory=overrides_embedder or PinnedFakeEmbedder, fetcher=fetcher,
                  connect_fn=lambda: connect(database_url))


def start(conn, history=HISTORY):
    versions.init_version(conn)
    row = seed_analysis_input(conn, history=history)
    jobs.enqueue(conn, f"analysis:{row['id']}", "ANALYSIS", {"analysis_input_id": row["id"]})
    return row


def job_rows(conn):
    return conn.execute("SELECT job_type, status, failure_code, job_key FROM ai_reply.ai_job ORDER BY id").fetchall()


def test_analysis_then_generic_end_to_end(conn, database_url, sample_video):
    row = start(conn)
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(4)],
                                 "gate": [gate_output(["G1", "G2", "G3", "G4"], quality=4, groups={"G4": "grp-G1"})]})
    w = worker(database_url, provider, FakeFetcher(sample_video))
    assert w.run_once(conn, "t").job_type == "ANALYSIS"
    snapshot = conn.execute("SELECT * FROM ai_reply.analysis_snapshot").fetchone()
    assert snapshot["status"] == "READY" and snapshot["history_input_status"] == "COMPLETE" and snapshot["history_used"]
    assert [f["position_percent"] for f in snapshot["frames"]] == [10, 50, 90] and snapshot["media_sha"]
    cited = conn.execute("SELECT source_id FROM ai_reply.evidence_source WHERE cited").fetchall()
    assert [r["source_id"] for r in cited] == ["chat:1"]
    sent = provider.calls[0]
    assert len(sent.images) == 3 and sent.payload["history_records"] == HISTORY and "actual_reply" not in sent.payload

    assert w.run_once(conn, "t").job_type == "GENERIC"
    generation_payload = provider.calls[1].payload
    assert [r["source_id"] for r in generation_payload["history_records"]] == ["chat:1"]  # 공개된 분석 근거만
    assert generation_payload["personal_history"] == [] and generation_payload["base_cards"] == []
    rset = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    assert rset["status"] == "READY" and rset["kind"] == "GENERIC" and rset["viewer_id"] is None
    assert [c["category"] for c in rset["cards"]] == ["question", "support", "empathy"]  # G4 는 G1 과 의미 중복
    assert all(set(c) == {"card_id", "category", "title"} for c in rset["cards"])
    assert {c["card_id"] for c in rset["cards"]} == {c["card_id"] for c in rset["internal_cards"]}
    attempt = conn.execute("SELECT * FROM ai_reply.generation_attempt").fetchone()
    assert attempt["attempt_key"] == job_rows(conn)[1]["job_key"] and attempt["result_set_id"] == rset["set_id"]
    assert [r["status"] for r in job_rows(conn)] == ["SUCCEEDED", "SUCCEEDED"]
    assert float(conn.execute("SELECT spent_usd FROM ai_reply.budget_day").fetchone()["spent_usd"]) > 0
    assert row["id"] == snapshot["analysis_input_id"]


def test_hist04_over_budget_sends_current_video_only(conn, database_url, sample_video):
    """HIST04: 입력 예산 초과 시 기록을 몰래 자르지 않고 현재 영상만 분석, OVER_BUDGET 으로 남긴다."""
    start(conn)
    provider = ScriptedProvider({"analysis": [analysis_output(linked=False)]})
    w = worker(database_url, provider, FakeFetcher(sample_video), input_token_budget=500)
    w.run_once(conn, "t")
    assert provider.calls[0].payload["history_records"] == []
    snapshot = conn.execute("SELECT history_input_status FROM ai_reply.analysis_snapshot").fetchone()
    assert snapshot["history_input_status"] == "OVER_BUDGET"


def test_semantic_invalid_analysis_fails_and_makes_no_cards(conn, database_url, sample_video):
    start(conn)
    bad = analysis_output()
    bad["context_links"][0]["source_quotes"][0]["quote"] = "원문에 없는 말"
    w = worker(database_url, ScriptedProvider({"analysis": [bad]}), FakeFetcher(sample_video))
    w.run_once(conn, "t")
    assert job_rows(conn)[0]["status"] == "FAILED" and job_rows(conn)[0]["failure_code"] == errors.SEMANTIC_INVALID
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.analysis_snapshot").fetchone()["n"] == 0
    assert len(job_rows(conn)) == 1


def test_media_from_unlisted_host_is_not_fetched(conn, database_url):
    versions.init_version(conn)
    row = seed_analysis_input(conn, media_url="https://evil.example.com/v.mp4")
    jobs.enqueue(conn, f"analysis:{row['id']}", "ANALYSIS", {"analysis_input_id": row["id"]})
    settings = Settings(database_url=database_url, media_allowed_prefixes=("http://media.test/",))
    w = Worker(settings, provider=ScriptedProvider({}), embedder_factory=HashEmbedder, fetcher=MediaFetcher(settings),
               connect_fn=lambda: connect(database_url))
    w.run_once(conn, "t")
    assert job_rows(conn)[0]["failure_code"] == errors.MEDIA_SOURCE_NOT_ALLOWED


def test_async03_gate_refusal_shows_no_unverified_cards(conn, database_url, sample_video):
    """ASYNC03: 검수가 거절·불완전이면 미검수 후보를 내보내지 않는다(세트 없음 → 앱은 UNAVAILABLE)."""
    start(conn)
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output()],
                                 "gate": [errors.JobFailure(errors.REFUSAL, "gate")]})
    w = worker(database_url, provider, FakeFetcher(sample_video))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    assert job_rows(conn)[1]["status"] == "FAILED" and job_rows(conn)[1]["failure_code"] == errors.REFUSAL
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.recommendation_set").fetchone()["n"] == 0


def test_gen01_gate_rejecting_all_gives_empty_set(conn, database_url, sample_video):
    start(conn)
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output()],
                                 "gate": [gate_output(["G1", "G2", "G3"], quality=2)]})
    w = worker(database_url, provider, FakeFetcher(sample_video))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    rset = conn.execute("SELECT status, cards FROM ai_reply.recommendation_set").fetchone()
    assert rset == {"status": "EMPTY", "cards": []}


def test_resume_after_transport_failure_reuses_valid_stage(conn, database_url, sample_video):
    """이미 받은 유효 결과(일반 후보)는 재개 때 다시 만들지 않는다. 전송 실패한 검수만 다시 부른다."""
    start(conn)
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output()],
                                 "gate": [errors.transport("503"), gate_output(["G1", "G2", "G3"])]})
    w = worker(database_url, provider, FakeFetcher(sample_video))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    assert job_rows(conn)[1]["status"] == "RETRY_WAIT"
    conn.execute("UPDATE ai_reply.ai_job SET next_attempt_at = now()")
    w.run_once(conn, "t")
    assert job_rows(conn)[1]["status"] == "SUCCEEDED"
    assert [c.stage for c in provider.calls] == ["analysis", "generic", "gate", "gate"]


def test_async02_redelivered_jobs_keep_one_result(conn, database_url, sample_video):
    """ASYNC02: 같은 작업이 다시 전달돼도 유효 결과·세트는 하나."""
    start(conn)
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output()], "gate": [gate_output(["G1", "G2", "G3"])]})
    fetcher = FakeFetcher(sample_video)
    w = worker(database_url, provider, fetcher)
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    conn.execute("UPDATE ai_reply.ai_job SET status = 'QUEUED', next_attempt_at = now(), attempt_count = 0")  # 재전달
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.analysis_snapshot").fetchone()["n"] == 1
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.recommendation_set").fetchone()["n"] == 1
    assert len(provider.calls) == 3 and fetcher.calls == 1


def test_retrieval_uses_gallery_with_aliases_and_records_ranks(conn, database_url, sample_video):
    versions.init_version(conn)
    data = {"provenance": "SYNTHETIC", "references": [
        {"reference_key": "syn-desk", "caption": "시험 기간 카페 공부", "current_visual_description": "카페 테이블에 노트북과 책이 있다.",
         "video_posted_at": "2026-11-01T03:00:00Z", "replies": [{"reply_key": "r1", "text": "무슨 시험이에요?", "posted_at": "2026-11-01T04:00:00Z"}]},
        {"reference_key": "syn-run", "caption": "퇴근 러닝", "current_visual_description": "강변을 달리는 운동화가 보인다.",
         "video_posted_at": "2026-11-01T03:00:00Z", "replies": [{"reply_key": "r1", "text": "몇 킬로 뛰어요?", "posted_at": "2026-11-01T04:00:00Z"}]}]}
    imported = gallery.import_gallery(conn, HashEmbedder(), data)
    current = versions.active(conn)
    derived = versions.derive(conn, current, "dev", gallery_snapshot_id=imported["gallery_snapshot_id"])
    versions.activate(conn, derived["version_id"], current["version_id"], current["revision"], "test gallery")
    row = seed_analysis_input(conn, history=HISTORY, preprocess_version=derived["preprocess_version"])
    jobs.enqueue(conn, f"analysis:{row['id']}", "ANALYSIS", {"analysis_input_id": row["id"]})
    out = candidates_output()
    out["candidates"][0]["reference_ids"] = ["R1"]
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [out], "gate": [gate_output(["G1", "G2", "G3"])]})
    w = worker(database_url, provider, FakeFetcher(sample_video))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    refs = provider.calls[1].payload["references"]
    assert [r["reference_id"] for r in refs] == ["R1", "R2"] and refs[0]["replies"][0]["reply_id"] == "R1-1"
    retrieval = conn.execute("SELECT retrieval FROM ai_reply.generation_attempt").fetchone()["retrieval"]
    version = retrieval["embedding_version"]
    assert (version["provider"], version["model_id"], version["request_mode"]) == \
        ("cloudflare_workers_ai", "@cf/qwen/qwen3-embedding-0.6b", "text") and len(retrieval["ranked"]) == 2
    assert retrieval["query_sha"] and retrieval["cosine_threshold"] is None
    stored = conn.execute("""SELECT DISTINCT provider, model_revision, request_mode, role, normalization
                               FROM ai_reply.embedding WHERE provider = 'cloudflare_workers_ai'""").fetchall()
    assert [tuple(r.values()) for r in stored] == [("cloudflare_workers_ai", "unpinned-test", "text", "document", "l2")]


def test_worker_embedder_other_than_version_pins_is_refused(conn, database_url, sample_video):
    """버전이 고정한 임베딩 체계(운영 Cloudflare)와 작업기 설정이 다르면 섞지 않고 추천을 만들지 않는다."""
    versions.init_version(conn)
    data = {"provenance": "SYNTHETIC", "references": [
        {"reference_key": "syn-run", "caption": "퇴근 러닝", "current_visual_description": "강변을 달리는 운동화가 보인다.",
         "video_posted_at": "2026-11-01T03:00:00Z", "replies": [{"reply_key": "r1", "text": "몇 킬로 뛰어요?", "posted_at": "2026-11-01T04:00:00Z"}]}]}
    imported = gallery.import_gallery(conn, HashEmbedder(), data)
    current = versions.active(conn)
    derived = versions.derive(conn, current, "dev", gallery_snapshot_id=imported["gallery_snapshot_id"])
    versions.activate(conn, derived["version_id"], current["version_id"], current["revision"], "test gallery")
    row = seed_analysis_input(conn, history=HISTORY, preprocess_version=derived["preprocess_version"])
    jobs.enqueue(conn, f"analysis:{row['id']}", "ANALYSIS", {"analysis_input_id": row["id"]})
    provider = ScriptedProvider({"analysis": [analysis_output()]})
    w = worker(database_url, provider, FakeFetcher(sample_video), embedder=HashEmbedder)
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    generic = conn.execute("""SELECT status, failure_code, failure_detail FROM ai_reply.ai_job
                               WHERE job_type = 'GENERIC'""").fetchone()
    assert generic["status"] == "FAILED" and generic["failure_code"] == errors.EMBEDDING_UNAVAILABLE
    assert "differs from version config" in generic["failure_detail"]
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.recommendation_set").fetchone()["n"] == 0


def test_ret04_vectors_of_other_models_are_never_compared(conn):
    """RET04: 모델·차원이 다른 벡터는 비교하지 않고 이 임베딩 체계로 새로 만든다."""
    data = {"provenance": "SYNTHETIC", "references": [
        {"reference_key": "syn-a", "caption": "a", "current_visual_description": "b", "video_posted_at": "2026-11-01T03:00:00Z",
         "replies": [{"reply_key": "r1", "text": "t", "posted_at": "2026-11-01T04:00:00Z"}]}]}
    imported = gallery.import_gallery(conn, HashEmbedder(), data)
    rid = conn.execute("SELECT id FROM ai_reply.reference_video").fetchone()["id"]
    for provider, model, mode in (("local_http", "other/model", "text"), ("test_hash", "test/hash-bigram", "queries")):
        # 다른 모델, 그리고 같은 모델이라도 요청 방식이 다른 벡터(0 벡터로 심어 둠)
        conn.execute("""INSERT INTO ai_reply.embedding (reference_video_id, provider, model_id, model_revision, request_mode,
                                                        dimensions, text_sha, vector)
                        VALUES (%s, %s, %s, 'v1', %s, 1024, %s, %s)""",
                     (rid, provider, model, mode, sha256_text("a\nb"), [0.0] * 1024))
    vectors = gallery.ensure_embeddings(conn, HashEmbedder(), [rid])
    assert abs(float(sum(x * x for x in vectors[rid])) - 1.0) < 1e-3  # 0 벡터(다른 체계)를 쓰지 않았다
    assert imported["references"] == 1


class ShortLimitEmbedder(PinnedFakeEmbedder):
    """입력 상한이 아주 짧은 임베더(Cloudflare 의 4,096바이트 상한을 흉내)."""

    def fits(self, text):
        return len(text.encode("utf-8")) <= 40


def test_reference_over_input_limit_gets_no_vector_and_is_resumable(conn):
    """상한을 넘는 참고 영상 설명은 자르지 않고 벡터를 만들지 않는다. 다시 부르면 남은 것만 채운다(재색인 이어 하기)."""
    data = {"provenance": "SYNTHETIC", "references": [
        {"reference_key": "short", "caption": "러닝", "current_visual_description": "운동화",
         "video_posted_at": "2026-11-01T03:00:00Z", "replies": [{"reply_key": "r1", "text": "t", "posted_at": "2026-11-01T04:00:00Z"}]},
        {"reference_key": "long", "caption": "아주 긴 설명", "current_visual_description": "강변 " * 40,
         "video_posted_at": "2026-11-01T03:00:00Z", "replies": [{"reply_key": "r1", "text": "t", "posted_at": "2026-11-01T04:00:00Z"}]}]}
    imported = gallery.import_gallery(conn, HashEmbedder(), data)
    first = gallery.reindex(conn, ShortLimitEmbedder(), imported["gallery_snapshot_id"])
    assert (first["references"], first["embedded"], first["skipped_too_long"]) == (2, 1, 1)
    count = "SELECT count(*) AS n FROM ai_reply.embedding WHERE provider = 'cloudflare_workers_ai'"
    assert conn.execute(count).fetchone()["n"] == 1
    again = gallery.reindex(conn, ShortLimitEmbedder(), imported["gallery_snapshot_id"])
    assert again["embedded"] == 1 and conn.execute(count).fetchone()["n"] == 1  # 이미 있는 벡터는 다시 만들지 않는다


def personal_setup(conn, database_url, sample_video, records):
    start(conn)
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(2)],
                                 "gate": [gate_output(["G1", "G2"], quality=3)]})
    w = worker(database_url, provider, FakeFetcher(sample_video))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    base = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    current = versions.active(conn)
    pinput = conn.execute(
        """INSERT INTO ai_reply.personal_input (viewer_id, video_id, analysis_id, base_set_id, personal_records,
               personal_snapshot_hash, version_id) VALUES (3, 101, %s, %s, %s, %s, %s) RETURNING id""",
        (base["analysis_id"], base["set_id"], jsonb(records), uuid.uuid4().hex, current["version_id"])).fetchone()
    jobs.enqueue(conn, f"personal:{pinput['id']}", "PERSONAL", {"personal_input_id": pinput["id"]})
    return provider, w, base


def personal_candidate(cid, source_id, quote, category="self_disclosure"):
    return {"candidate_id": cid, "category": category, "title": "나도 밤샌 얘기를 해 볼까요?", "example": "저도 어제 과제 하느라 밤샜어요",
            "current_anchor": "과제 마감", "reference_ids": [],
            "personalization": {"kind": "past_experience", "source_quotes": [{"source_id": source_id, "quote": quote}],
                                "actor_ids": ["3"], "delta": "시청자의 밤샘 경험을 연결"}}


def test_per01_other_people_experience_only_keeps_generic_set(conn, database_url, sample_video):
    """PER01: 타인의 경험만 있으면 개인화하지 않고 일반 세트(ID·내용·순서)를 그대로 쓴다."""
    records = [record("chat:7", "8", "나 어제 과제 밤샜어", role="parent_context")]
    provider, w, base = personal_setup(conn, database_url, sample_video, records)
    provider.responses["personal"] = [{"candidates": [personal_candidate("P1", "chat:7", "어제 과제 밤샜어")],
                                       "personalization_used": True, "reason": "개인 기록"}]
    w.run_once(conn, "t")
    attempt = conn.execute("SELECT * FROM ai_reply.generation_attempt WHERE stage = 'PERSONAL'").fetchone()
    assert attempt["result_set_id"] == base["set_id"]
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.recommendation_set").fetchone()["n"] == 1
    assert "gate" not in [c.stage for c in provider.calls[3:]]  # 남은 후보가 없으면 검수를 부르지 않는다


def test_personal_candidate_adopted_creates_viewer_set_keeping_generic_ids(conn, database_url, sample_video):
    records = [record("chat:9", "3", "저도 어제 과제 하느라 밤샜어요", role="own")]
    provider, w, base = personal_setup(conn, database_url, sample_video, records)
    provider.responses["personal"] = [{"candidates": [personal_candidate("P1", "chat:9", "과제 하느라 밤샜어요")],
                                       "personalization_used": True, "reason": "개인 기록"}]
    provider.responses["gate"] = [gate_output(["P1"], quality=4, useful=True)]
    w.run_once(conn, "t")
    personal = conn.execute("SELECT * FROM ai_reply.recommendation_set WHERE kind = 'PERSONAL'").fetchone()
    assert personal["viewer_id"] == 3 and personal["base_set_id"] == base["set_id"]
    base_ids = [c["card_id"] for c in base["cards"]]
    assert [c["card_id"] for c in personal["cards"]][:2] == base_ids and len(personal["cards"]) == 3
    gate_payload = provider.calls[-1].payload
    assert [b["card_id"] for b in gate_payload["base_cards"]] == base_ids


def test_del01_purge_blocks_derived_data(conn, database_url, sample_video):
    """DEL01: 삭제된 영상은 분석·세트·참고 사례·벡터 사용을 바로 막는다."""
    start(conn)
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output()], "gate": [gate_output(["G1", "G2", "G3"])]})
    w = worker(database_url, provider, FakeFetcher(sample_video))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    conn.execute("""INSERT INTO ai_reply.reference_video (reference_key, source_video_id, caption, current_visual_description,
                        video_posted_at, provenance, approval) VALUES ('real-101', 101, 'c', 'd', %s, 'QA', 'APPROVED')""", (T0,))
    jobs.enqueue(conn, "purge-video:101:DELETED", "PURGE_VIDEO", {"video_id": 101, "reason": "DELETED"})
    w.run_once(conn, "t")
    assert conn.execute("SELECT status FROM ai_reply.analysis_snapshot").fetchone()["status"] == "REVOKED"
    assert conn.execute("SELECT revoked_at FROM ai_reply.recommendation_set").fetchone()["revoked_at"] is not None
    assert conn.execute("SELECT deleted_at FROM ai_reply.reference_video").fetchone()["deleted_at"] is not None

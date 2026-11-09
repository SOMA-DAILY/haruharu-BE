"""실제 GPT-6.1 Terra·임베딩 서비스로 한 번 끝까지 돈다(기본 제외: `pytest -m live`).

필요: OPENAI_API_KEY(또는 REPLY_AI_DOTENV), 임베딩 서비스(REPLY_AI_EMBEDDING_URL, 기본 127.0.0.1:18452),
REPLY_AI_LIVE_VIDEO(얼굴 없는 3초 테스트 영상). 합성 자료만 쓴다.
"""
import json
import os
import threading
import uuid
from datetime import timedelta
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import pytest

from reply_ai import gallery, jobs, versions
from reply_ai.config import Settings, load_dotenv_secret
from reply_ai.db import connect, jsonb
from reply_ai.embedding import HttpEmbedder
from reply_ai.feedback.engine import Engine, schedule
from reply_ai.media import MediaFetcher
from reply_ai.provider import OpenAIProvider
from reply_ai.util import now_utc, sha256_json
from reply_ai.worker import Worker

from .conftest import record, seed_analysis_input

pytestmark = [pytest.mark.live, pytest.mark.pg]
FIXTURES = Path(__file__).resolve().parents[1] / "fixtures"


@pytest.fixture(scope="module")
def media_server():
    video = Path(os.environ.get("REPLY_AI_LIVE_VIDEO", ""))
    if not video.is_file():
        pytest.skip("REPLY_AI_LIVE_VIDEO not set")
    handler = partial(SimpleHTTPRequestHandler, directory=str(video.parent))
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{server.server_address[1]}/", video.name
    server.shutdown()


def test_live_analysis_generic_gate_and_feedback(conn, database_url, media_server):
    if os.environ.get("REPLY_AI_DOTENV"):
        load_dotenv_secret(os.environ["REPLY_AI_DOTENV"])
    prefix, name = media_server
    settings = Settings.from_env()
    settings = Settings(**{**settings.__dict__, "database_url": database_url, "media_allowed_prefixes": (prefix,)})
    embedder = HttpEmbedder(settings.embedding_url)
    versions.init_version(conn)
    imported = gallery.import_gallery(conn, embedder, json.loads((FIXTURES / "synthetic_gallery.json").read_text(encoding="utf-8")))
    current = versions.active(conn)
    dev = versions.derive(conn, current, "dev", gallery_snapshot_id=imported["gallery_snapshot_id"], note="live smoke")
    versions.activate(conn, dev["version_id"], current["version_id"], current["revision"], "live smoke gallery")

    history = [record("chat:1", "11", "내일까지 과제 내야 해서 오늘 밤샐 듯 ㅠㅠ", minutes_before=600),
               record("chat:2", "3", "헉 화이팅이에요! 무슨 과제예요?", minutes_before=590, parents=["chat:1"]),
               record("chat:3", "11", "경영학 레포트요 ㅋㅋ 커피 수혈 중", minutes_before=585, parents=["chat:2"])]
    row = seed_analysis_input(conn, caption="과제 마감 D-1", media_url=prefix + name, history=history,
                              preprocess_version=dev["preprocess_version"])
    jobs.enqueue(conn, f"analysis:{row['id']}", "ANALYSIS", {"analysis_input_id": row["id"]})
    provider = OpenAIProvider(settings)
    worker = Worker(settings, provider=provider, embedder_factory=lambda: embedder, fetcher=MediaFetcher(settings),
                    connect_fn=lambda: connect(database_url))
    assert worker.run_once(conn, "live").job_type == "ANALYSIS"
    assert worker.run_once(conn, "live").job_type == "GENERIC"
    states = conn.execute("SELECT job_type, status, failure_code FROM ai_reply.ai_job ORDER BY id").fetchall()
    assert all(s["status"] == "SUCCEEDED" for s in states), states
    snapshot = conn.execute("SELECT * FROM ai_reply.analysis_snapshot").fetchone()
    rset = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    attempt = conn.execute("SELECT retrieval, candidates, cost_usd FROM ai_reply.generation_attempt").fetchone()
    assert rset["status"] in ("READY", "EMPTY") and len(rset["cards"]) <= 3
    print("\nLIVE analysis:", json.dumps({"history_used": snapshot["history_used"], "requirement": snapshot["history_requirement"],
                                         "current": snapshot["current_visual_description"],
                                         "contextual": snapshot["contextual_description"],
                                         "cost_usd": float(snapshot["cost_usd"])}, ensure_ascii=False))
    print("LIVE retrieval:", [(r["reference_key"], r["cosine"]) for r in attempt["retrieval"]["ranked"]])
    print("LIVE candidates:", [(c["candidate_id"], c["category"], c["title"]) for c in attempt["candidates"]["kept"]],
          "dropped:", attempt["candidates"]["dropped"])
    print("LIVE cards:", [(c["category"], c["title"]) for c in rset["cards"]], "generic+gate cost:", float(attempt["cost_usd"]))

    # 아주 작은 피드백 배치(1·2단계 실제 판정): 합성 episode 하나 → 적절성·방향 판정 → 원인 1건이라 기존 버전 유지.
    closed = now_utc() - timedelta(minutes=5)
    episode = conn.execute(
        """INSERT INTO ai_reply.reply_episode (session_id, set_id, request_id, viewer_id, video_id, room_id, first_impression_at,
               last_impression_at, actual_reply_message_id, actual_reply_at, attribution, outcome, closed_at, provenance)
           VALUES (%s, %s, %s, 3, 101, 7, %s, %s, 1001, %s, 'explicit', 'REPLIED', %s, 'SYNTHETIC') RETURNING id""",
        (str(uuid.uuid4()), rset["set_id"], str(uuid.uuid4()), closed - timedelta(minutes=10), closed - timedelta(minutes=10),
         closed, closed)).fetchone()
    reply = "경영학 레포트 주제가 뭐예요? 커피 몇 잔째예요 ㅋㅋ"
    conn.execute("""INSERT INTO ai_reply.episode_reply_snapshot (episode_id, message_id, reply_text, reply_sha, provenance)
                    VALUES (%s, 1001, %s, %s, 'SYNTHETIC')""", (episode["id"], reply, sha256_json(reply)))
    scheduled = schedule(conn, settings, force=True)
    result = Engine(conn, settings, provider).tick(scheduled["batch_run_id"])
    judgments = conn.execute("SELECT stage, verdict FROM ai_reply.feedback_judgment ORDER BY id").fetchall()
    spent = conn.execute("SELECT spent_usd FROM ai_reply.batch_run").fetchone()["spent_usd"]
    print("LIVE batch:", result["status"], result.get("decision"), result.get("observations"), judgments, "cost:", float(spent))
    online = conn.execute("SELECT spent_usd FROM ai_reply.budget_day").fetchone()["spent_usd"]
    print("LIVE total cost usd:", round(float(online) + float(spent), 6))
    assert result["status"] == "KEPT_BASELINE" and len(judgments) == 2

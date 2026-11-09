"""PostgreSQL 통합 테스트: 127.0.0.1:5465 컨테이너에 reply_ai_test_* DB 를 만들고 up-script + 제안을 적용한다.

haruharu_devstack 등 다른 DB 는 건드리지 않는다. 테스트마다 ai_reply 표를 비운다.
"""
from __future__ import annotations

import os
import subprocess
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

import psycopg
import pytest

from reply_ai.db import connect, jsonb
from reply_ai.embedding import HashEmbedder
from reply_ai.resources import package_config
from reply_ai.util import sha256_json, sha256_text

ROOT = Path(__file__).resolve().parents[1]
UP = ROOT.parent / "scripts" / "sql" / "558-reply-recommendations-up.sql"
ADMIN_URL = os.environ.get("REPLY_AI_TEST_ADMIN_URL", "postgresql://haruharu@127.0.0.1:5465/postgres")


class PinnedFakeEmbedder(HashEmbedder):
    """버전이 고정한 임베딩 체계(운영: Cloudflare)를 흉내 내는 결정적 대역. 벡터는 해시라 실제 모델과 무관하고 테스트 DB 에만 남는다."""

    def info(self):
        pinned = package_config()["embedding"]
        return {"provider": pinned["provider"], "model_id": pinned["model_id"], "model_revision": "unpinned-test",
                "request_mode": pinned["request_mode"], "dimensions": self.dimensions, "normalization": "l2"}


def _admin():
    return psycopg.connect(ADMIN_URL, autocommit=True)


@pytest.fixture(scope="session")
def database_url():
    name = f"reply_ai_test_{os.getpid()}_{uuid.uuid4().hex[:6]}"
    try:
        with _admin() as admin:
            admin.execute(f'CREATE DATABASE "{name}"')
    except psycopg.OperationalError as error:
        pytest.skip(f"test PostgreSQL unavailable: {type(error).__name__}")
    url = ADMIN_URL.rsplit("/", 1)[0] + "/" + name
    with psycopg.connect(url, autocommit=True) as conn:
        conn.execute(UP.read_text(encoding="utf-8"))
    yield url
    with _admin() as admin:
        admin.execute(f'DROP DATABASE IF EXISTS "{name}" WITH (FORCE)')


@pytest.fixture()
def conn(database_url):
    connection = connect(database_url)
    tables = [r["tablename"] for r in connection.execute("SELECT tablename FROM pg_tables WHERE schemaname = 'ai_reply'")]
    connection.execute("TRUNCATE " + ", ".join(f"ai_reply.{t}" for t in tables) + " RESTART IDENTITY CASCADE")
    yield connection
    connection.close()


T0 = datetime(2026, 11, 8, 3, 0, tzinfo=timezone.utc)


def record(source_id: str, actor: str, text: str, minutes_before: int = 30, parents: list[str] | None = None,
           role: str = "room", kind: str = "chat") -> dict:
    at = (T0 - timedelta(minutes=minutes_before)).isoformat().replace("+00:00", "Z")
    return {"source_id": source_id, "actor_id": actor, "kind": kind, "role": role, "occurred_at": at, "available_at": at,
            "revision": sha256_text(text), "text": text, "parent_source_ids": parents or []}


def seed_analysis_input(conn, *, video_id: int = 101, room_id: int = 7, author_id: int = 11, caption: str = "과제 마감 D-1",
                        media_url: str = "http://media.test/v/101.mp4", history: list[dict] | None = None,
                        preprocess_version: str | None = None, scope_hash: str | None = None) -> dict:
    """앱 서버가 하는 일(분석 요청 + 권한 확인 스냅샷 + 출처)을 SQL 로 흉내 낸다."""
    from reply_ai import versions
    history = history or []
    current = versions.active(conn)
    preprocess = preprocess_version or (current["preprocess_version"] if current else "pre-test")
    scope = scope_hash or sha256_json([])
    request = conn.execute(
        """INSERT INTO ai_reply.analysis_request (video_id, room_id, author_id, scope_hash, excluded_actor_ids, caption_revision,
               media_revision, preprocess_version, origin, status)
           VALUES (%s, %s, %s, %s, '[]'::jsonb, %s, %s, %s, 'PUBLISH', 'SNAPSHOT_READY') RETURNING id""",
        (video_id, room_id, author_id, scope, sha256_text(caption), sha256_text(media_url), preprocess)).fetchone()
    row = conn.execute(
        """INSERT INTO ai_reply.analysis_input (analysis_request_id, video_id, room_id, author_id, published_at, caption,
               caption_revision, media_url, media_revision, scope_hash, history_records, history_snapshot_hash, history_status,
               excluded_sources, preprocess_version)
           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, '[]'::jsonb, %s) RETURNING *""",
        (request["id"], video_id, room_id, author_id, T0, caption, sha256_text(caption), media_url, sha256_text(media_url),
         scope, jsonb(history), sha256_json(history), "COMPLETE" if history else "NO_RECORDS", preprocess)).fetchone()
    conn.execute("UPDATE ai_reply.analysis_request SET analysis_input_id = %s WHERE id = %s", (row["id"], request["id"]))
    for item in history:
        kind, ref = item["source_id"].split(":", 1)
        conn.execute(
            """INSERT INTO ai_reply.evidence_source (owner_kind, owner_id, source_id, source_kind, source_ref_id, actor_id,
                   occurred_at, available_at, revision, text_sha, role)
               VALUES ('ANALYSIS', %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
            (row["id"], item["source_id"], item["kind"], int(ref), item["actor_id"], item["occurred_at"], item["available_at"],
             item["revision"], sha256_text(item["text"]), item["role"]))
    return row


@pytest.fixture(scope="session")
def sample_video(tmp_path_factory) -> bytes:
    """얼굴 없는 그림 장면(책상 위 노트북·컵) 3초 영상."""
    out = tmp_path_factory.mktemp("media") / "scene.mp4"
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
                    "color=c=0xd9c7a7:s=360x640:d=3,drawbox=x=40:y=300:w=280:h=170:color=0x333333:t=fill,"
                    "drawbox=x=60:y=315:w=240:h=130:color=0x88bbee:t=fill,drawbox=x=250:y=480:w=50:h=60:color=0xffffff:t=fill",
                    "-r", "30", "-pix_fmt", "yuv420p", "-c:v", "libx264", str(out)], check=True, timeout=60)
    return out.read_bytes()

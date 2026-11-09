"""참고 사례 저장소(6장). 사례·답장은 한 번 넣으면 바꾸지 않고, 묶음(gallery_snapshot)도 새 id 로만 만든다."""
from __future__ import annotations

from typing import Any

import numpy as np

from .db import jsonb
from .embedding import Embedder, fits, version_of
from .retrieval import document_text
from .util import parse_iso, sha256_json, sha256_text

PROVENANCES = {"SYNTHETIC", "QA", "REAL_USER"}


def import_gallery(conn, embedder: Embedder, data: dict[str, Any], note: str | None = None) -> dict[str, Any]:
    """{"provenance": "SYNTHETIC", "references": [{reference_key, caption, current_visual_description, video_posted_at,
    source_room_id?, replies:[{reply_key, text, posted_at}]}]} → 새 gallery_snapshot(CANDIDATE)."""
    provenance = data.get("provenance")
    if provenance not in PROVENANCES:
        raise ValueError("provenance must be one of " + ", ".join(sorted(PROVENANCES)))
    if provenance == "REAL_USER":
        raise ValueError("REAL_USER references need an approved de-identified export path (not available)")
    ids: list[int] = []
    with conn.transaction():
        for ref in data["references"]:
            key = ref["reference_key"]
            posted = parse_iso(ref["video_posted_at"])
            row = conn.execute("""SELECT id, caption, current_visual_description, video_posted_at, provenance
                                    FROM ai_reply.reference_video WHERE reference_key = %s""", (key,)).fetchone()
            if row is None:
                row = conn.execute(
                    """INSERT INTO ai_reply.reference_video (reference_key, source_video_id, source_room_id, caption,
                           current_visual_description, video_posted_at, media_sha, frame_shas, provenance, approval)
                       VALUES (%s, NULL, %s, %s, %s, %s, NULL, '[]'::jsonb, %s, 'APPROVED') RETURNING id""",
                    (key, ref.get("source_room_id"), ref["caption"], ref["current_visual_description"], posted,
                     provenance)).fetchone()
            elif (row["caption"], row["current_visual_description"], row["video_posted_at"], row["provenance"]) != \
                    (ref["caption"], ref["current_visual_description"], posted, provenance):
                raise ValueError(f"reference {key} already exists with different content (references are immutable)")
            for reply in ref["replies"]:
                conn.execute(
                    """INSERT INTO ai_reply.reference_pair (reference_video_id, reply_key, sanitized_text, reply_posted_at,
                           provenance, approval, eligible)
                       VALUES (%s, %s, %s, %s, %s, 'APPROVED', true) ON CONFLICT DO NOTHING""",
                    (row["id"], reply["reply_key"], reply["text"], parse_iso(reply["posted_at"]), provenance))
            ids.append(row["id"])
        ensure_embeddings(conn, embedder, ids)
        ids = sorted(set(ids))
        content = sha256_json({"reference_video_ids": ids})
        snapshot_id = "gallery-" + content[:16]
        conn.execute(
            """INSERT INTO ai_reply.gallery_snapshot (id, reference_video_ids, content_sha, status, note)
               VALUES (%s, %s, %s, 'CANDIDATE', %s) ON CONFLICT DO NOTHING""",
            (snapshot_id, jsonb(ids), content, (note or f"{provenance} {len(ids)}편")[:300]))
    return {"gallery_snapshot_id": snapshot_id, "references": len(ids), "provenance": provenance}


def ensure_embeddings(conn, embedder: Embedder, reference_video_ids: list[int]) -> dict[int, np.ndarray]:
    """참고 영상 문서 벡터를 이 임베딩 버전으로 준비한다(없으면 만들고, 있으면 그대로 쓴다). 벡터는 바꾸지 않는다.

    제공자·모델·revision·요청 방식·차원·정규화가 모두 같은 벡터만 쓴다(다른 체계와 섞지 않음). 중간에 끊겨도
    이미 저장된 벡터는 다시 만들지 않으니 다시 부르면 이어서 채운다. 입력 상한을 넘는 문서는 벡터를 만들지 않고
    빼 둔다(검색에서 no_vector 로 빠진다, 자르지 않음).
    """
    if not reference_video_ids:
        return {}
    version = version_of(embedder.info())
    rows = conn.execute(
        """SELECT id, caption, current_visual_description FROM ai_reply.reference_video WHERE id = ANY(%s)""",
        (list(reference_video_ids),)).fetchall()
    docs = {r["id"]: document_text(r["caption"], r["current_visual_description"]) for r in rows}
    existing = conn.execute(
        """SELECT reference_video_id, text_sha, vector FROM ai_reply.embedding
            WHERE reference_video_id = ANY(%s) AND provider = %s AND model_id = %s AND model_revision = %s
              AND request_mode = %s AND role = 'document' AND dimensions = %s AND normalization = %s""",
        (list(docs), version["provider"], version["model_id"], version["model_revision"], version["request_mode"],
         version["dimensions"], version["normalization"])).fetchall()
    vectors: dict[int, np.ndarray] = {}
    for row in existing:
        if row["text_sha"] == sha256_text(docs[row["reference_video_id"]]):
            vectors[row["reference_video_id"]] = np.asarray(row["vector"], dtype=np.float32)
    missing = [rid for rid in docs if rid not in vectors and fits(embedder, docs[rid])]
    for start in range(0, len(missing), 32):
        chunk = missing[start:start + 32]
        embedded = embedder.embed([docs[rid] for rid in chunk])
        for rid, vector in zip(chunk, embedded, strict=True):
            conn.execute(
                """INSERT INTO ai_reply.embedding (reference_video_id, provider, model_id, model_revision, request_mode,
                                                   role, dimensions, normalization, text_sha, vector)
                   VALUES (%s, %s, %s, %s, %s, 'document', %s, %s, %s, %s) ON CONFLICT DO NOTHING""",
                (rid, version["provider"], version["model_id"], version["model_revision"], version["request_mode"],
                 version["dimensions"], version["normalization"], sha256_text(docs[rid]), [float(x) for x in vector]))
            vectors[rid] = np.asarray(vector, dtype=np.float32)
    return vectors


def reindex(conn, embedder: Embedder, gallery_snapshot_id: str) -> dict[str, Any]:
    """묶음의 모든 참고 영상을 이 임베딩 버전으로 채운다(중단 후 다시 부르면 이어서). 버전을 활성화하지는 않는다."""
    snapshot = conn.execute("SELECT reference_video_ids FROM ai_reply.gallery_snapshot WHERE id = %s",
                            (gallery_snapshot_id,)).fetchone()
    if snapshot is None:
        raise ValueError(f"unknown gallery_snapshot {gallery_snapshot_id}")
    ids = [int(x) for x in snapshot["reference_video_ids"]]
    vectors = ensure_embeddings(conn, embedder, ids)
    return {"gallery_snapshot_id": gallery_snapshot_id, "embedding_version": version_of(embedder.info()),
            "references": len(ids), "embedded": len(vectors), "skipped_too_long": len(ids) - len(vectors)}


def load_gallery(conn, snapshot_id: str) -> tuple[list[dict[str, Any]], dict[int, list[dict[str, Any]]]]:
    snapshot = conn.execute("SELECT reference_video_ids FROM ai_reply.gallery_snapshot WHERE id = %s", (snapshot_id,)).fetchone()
    if snapshot is None:
        return [], {}
    ids = [int(x) for x in snapshot["reference_video_ids"]]
    if not ids:
        return [], {}
    refs = conn.execute("SELECT * FROM ai_reply.reference_video WHERE id = ANY(%s) ORDER BY id", (ids,)).fetchall()
    pairs: dict[int, list[dict[str, Any]]] = {}
    for row in conn.execute("SELECT * FROM ai_reply.reference_pair WHERE reference_video_id = ANY(%s)", (ids,)).fetchall():
        pairs.setdefault(row["reference_video_id"], []).append(row)
    return refs, pairs

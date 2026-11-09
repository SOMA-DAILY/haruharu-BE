"""유사 사례 검색(6장): 적격 참고 영상 전부에 대한 정확 코사인, 상위 3편, 영상당 답장 최대 5개.

- query = "Instruct: {query_instruction}\\nQuery:{caption}\\n{current_visual_description}" (instruction 한 번)
- document = "{caption}\\n{current_visual_description}" (instruction 없음, 실제 답장·개인 기록은 섞지 않는다)
- 유사도 임계값은 두지 않는다. 관련 없는 참조는 생성·검수가 무시한다.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

import numpy as np

from .util import sha256_text


def query_text(instruction: str, caption: str, description: str) -> str:
    return f"Instruct: {instruction}\nQuery:{caption}\n{description}"


def document_text(caption: str, description: str) -> str:
    return f"{caption}\n{description}"


@dataclass
class Current:
    video_id: int
    room_id: int
    published_at: datetime
    media_sha: str | None
    frame_shas: set[str] = field(default_factory=set)


@dataclass
class Protected:
    videos: set[str] = field(default_factory=set)
    media_shas: set[str] = field(default_factory=set)
    clusters: set[str] = field(default_factory=set)
    replies: set[str] = field(default_factory=set)

    @classmethod
    def from_rows(cls, rows: list[dict[str, Any]]) -> "Protected":
        result = cls()
        for row in rows:
            target = {"video": result.videos, "media_sha": result.media_shas, "media_cluster": result.clusters,
                      "reply": result.replies}.get(row["kind"])
            if target is not None:
                target.add(str(row["value"]))
        return result


def exclusion_reason(current: Current, ref: dict[str, Any], protected: Protected) -> str | None:
    if ref.get("deleted_at") is not None:
        return "deleted"
    if ref.get("approval") != "APPROVED":
        return "not_approved"
    if ref.get("protected") or (ref.get("source_video_id") is not None and str(ref["source_video_id"]) in protected.videos) \
            or (ref.get("media_sha") and ref["media_sha"] in protected.media_shas) \
            or (ref.get("media_cluster_id") and ref["media_cluster_id"] in protected.clusters):
        return "protected"
    if ref.get("source_video_id") is not None and ref["source_video_id"] == current.video_id:
        return "same_video"
    if ref.get("source_room_id") is not None and ref["source_room_id"] == current.room_id:
        return "same_room"
    if current.media_sha and ref.get("media_sha") == current.media_sha:
        return "media_duplicate"
    if current.frame_shas and set(ref.get("frame_shas") or []) & current.frame_shas:
        return "frame_overlap"
    if ref["video_posted_at"] >= current.published_at:
        return "future_video"
    return None


def eligible_replies(current: Current, pairs: list[dict[str, Any]], protected: Protected, limit: int) -> list[dict[str, Any]]:
    rows = [p for p in pairs
            if p.get("deleted_at") is None and p.get("approval") == "APPROVED" and p.get("eligible", True)
            and p["reply_posted_at"] < current.published_at and (p.get("sanitized_text") or "").strip()
            and (p.get("source_reply_id") is None or str(p["source_reply_id"]) not in protected.replies)
            and p["reply_key"] not in protected.replies]
    rows.sort(key=lambda p: (p["reply_posted_at"], p["reply_key"]))
    return rows[:limit]


@dataclass
class Retrieved:
    references: list[dict[str, Any]]          # 생성 입력용(별칭 R1…)
    record: dict[str, Any]                     # 검색 기록(generation_attempt.retrieval)
    alias_map: dict[str, dict[str, Any]]       # 별칭 → 참고 영상 행


def retrieve(current: Current, refs: list[dict[str, Any]], pairs_by_ref: dict[int, list[dict[str, Any]]],
             vectors: dict[int, np.ndarray], query_vector: np.ndarray | None, protected: Protected, *,
             query: str, embedding_version: dict[str, Any] | None, gallery_snapshot_id: str,
             top_k: int = 3, max_replies: int = 5) -> Retrieved:
    excluded: list[dict[str, Any]] = []
    candidates: list[tuple[float, dict[str, Any], list[dict[str, Any]]]] = []
    for ref in refs:
        reason = exclusion_reason(current, ref, protected)
        replies = [] if reason else eligible_replies(current, pairs_by_ref.get(ref["id"], []), protected, max_replies)
        if reason is None and not replies:
            reason = "no_replies"
        if reason is None and ref["id"] not in vectors:
            reason = "no_vector"
        if reason:
            excluded.append({"reference_key": ref["reference_key"], "reason": reason})
            continue
        cosine = float(np.dot(query_vector, vectors[ref["id"]])) if query_vector is not None else 0.0
        candidates.append((cosine, ref, replies))
    candidates.sort(key=lambda item: (-item[0], item[1]["reference_key"]))
    references, ranked, selected, alias_map = [], [], [], {}
    for index, (cosine, ref, replies) in enumerate(candidates[:top_k], start=1):
        alias = f"R{index}"
        alias_map[alias] = ref
        references.append({
            "reference_id": alias, "caption": ref["caption"], "current_visual_description": ref["current_visual_description"],
            "replies": [{"reply_id": f"{alias}-{j}", "text": p["sanitized_text"]} for j, p in enumerate(replies, start=1)],
        })
        ranked.append({"alias": alias, "reference_key": ref["reference_key"], "reference_video_id": ref["id"],
                       "cosine": round(cosine, 6)})
        selected.extend({"alias": f"{alias}-{j}", "reply_key": p["reply_key"], "reference_pair_id": p["id"]}
                        for j, p in enumerate(replies, start=1))
    record = {
        "query_sha": sha256_text(query), "embedding_version": embedding_version, "gallery_snapshot_id": gallery_snapshot_id,
        "top_k": top_k, "max_replies_per_video": max_replies, "cosine_threshold": None,
        "eligible_count": len(candidates), "gallery_count": len(refs), "excluded": excluded, "ranked": ranked,
        "selected_replies": selected,
    }
    return Retrieved(references=references, record=record, alias_map=alias_map)

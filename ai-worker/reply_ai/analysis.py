"""영상 이해(4장): 모델 입력 만들기와 결과 의미 검사(IMPLEMENTATION-NOTES §2)."""
from __future__ import annotations

from typing import Any

import jsonschema

from . import errors
from .resources import schema
from .util import iso, sha256_json

# 생성·검수 입력에 들어가면 안 되는 평가용 칸(EVAL01). 어느 깊이에 있어도 거부한다.
FORBIDDEN_KEYS = frozenset({
    "actual_reply", "actual_replies", "target_reply", "expected_reply", "labels", "label", "ground_truth",
    "selection_label", "covered_reply_ids", "matching_card_ids", "gold",
})


def forbid_evaluation_fields(value: Any, path: str = "") -> None:
    if isinstance(value, dict):
        leaked = FORBIDDEN_KEYS.intersection(value)
        if leaked:
            raise errors.JobFailure(errors.SCHEMA_INVALID, f"evaluation field in model input: {sorted(leaked)[0]}")
        for key, item in value.items():
            forbid_evaluation_fields(item, f"{path}/{key}")
    elif isinstance(value, list):
        for item in value:
            forbid_evaluation_fields(item, path)


def validate_input(schema_name: str, payload: dict[str, Any]) -> None:
    """모델에 보내기 전 입력 계약 검사(추가 칸 금지). 앱이 만든 스냅샷이 계약과 다르면 실패다."""
    forbid_evaluation_fields(payload)
    try:
        jsonschema.Draft202012Validator(schema(schema_name)).validate(payload)
    except jsonschema.ValidationError as error:
        location = "/".join(str(p) for p in error.absolute_path)
        raise errors.JobFailure(errors.INPUT_MISSING, f"{schema_name} contract: {error.validator} at /{location}") from None


def model_input(row: dict[str, Any], frames: list[dict[str, Any]], include_history: bool) -> dict[str, Any]:
    """analysis-input.schema.json 형식. include_history=False 면 기록 없이(OVER_BUDGET) 현재 영상만."""
    body = {
        "task_id": f"analysis-{row['id']}",
        "current": {
            "video_id": str(row["video_id"]),
            "author_id": str(row["author_id"]),
            "published_at": iso(row["published_at"]),
            "caption": row["caption"],
            "frames": frames,
        },
        "history_records": list(row["history_records"]) if include_history else [],
    }
    return {**body, "input_sha256": sha256_json(body)}


def analysis_key(row: dict[str, Any]) -> str:
    """명세 3장 분석 고유 키."""
    return sha256_json({
        "video_id": row["video_id"], "media_revision": row["media_revision"], "caption_revision": row["caption_revision"],
        "history_snapshot_hash": row["history_snapshot_hash"], "access_scope_hash": row["scope_hash"],
        "preprocess_version": row["preprocess_version"],
    })


def validate_output(output: dict[str, Any], payload: dict[str, Any]) -> set[str]:
    """스키마 검사를 통과한 분석 결과의 의미 검사. 통과하면 인용한 source_id 집합을 돌려준다."""
    frame_ids = {f["frame_id"] for f in payload["current"]["frames"]}
    records = {r["source_id"]: r for r in payload["history_records"]}
    contextual = output["contextual_description"]
    links = output["context_links"]

    for fact in output["visual_facts"]:
        if not fact["frame_ids"] or not set(fact["frame_ids"]) <= frame_ids:
            raise errors.semantic("visual_fact frame_ids")
    if len(links) > 3:
        raise errors.semantic("context_links > 3")
    if output["history_used"] != bool(links):
        raise errors.semantic("history_used must equal non-empty context_links")
    if output["history_requirement"] == "not_needed" and output["history_used"]:
        raise errors.semantic("not_needed with history_used")
    if not output["history_used"] and output["current_visual_description"] != contextual:
        raise errors.semantic("history_used=false needs identical descriptions")
    if not output["current_visual_description"].strip():
        raise errors.semantic("empty current description")

    cited: set[str] = set()
    link_ids: set[str] = set()
    for link in links:
        if link["link_id"] in link_ids:
            raise errors.semantic("duplicate link_id")
        link_ids.add(link["link_id"])
        span = link["description_span"]
        if not span.strip() or span not in contextual:
            raise errors.semantic("description_span not in contextual_description")
        if not link["source_quotes"]:
            raise errors.semantic("link without source quote")
        for quote in link["source_quotes"]:
            record = records.get(quote["source_id"])
            if record is None:
                raise errors.semantic("quote source not in input")
            if not quote["quote"].strip() or quote["quote"] not in record["text"]:
                raise errors.semantic("quote is not an exact substring of its source")
            cited.add(quote["source_id"])
    return cited


def cited_records(input_records: list[dict[str, Any]], output: dict[str, Any]) -> list[dict[str, Any]]:
    """생성·검수에 넣을 '공개된 분석 근거' = 분석 결과가 인용한 기록만(입력 순서 유지)."""
    ids = {q["source_id"] for link in output.get("context_links", []) for q in link["source_quotes"]}
    return [r for r in input_records if r["source_id"] in ids]

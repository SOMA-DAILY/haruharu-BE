"""후보 생성·검수·최종 선택(7·8장, IMPLEMENTATION-NOTES §4).

- 일반 후보 ≤10(G1…), 개인 후보 ≤6(P1…), 최종 ≤3, 유형당 ≤1, 의미 그룹당 ≤1. 0개도 정상.
- 후보 하나의 내용 규칙 위반(제목 형식·anchor·근거)은 그 후보만 검수 전에 버리고 기록한다.
  ID 중복·형식 위반·개인화 선언 불일치처럼 출력 전체가 계약을 어기면 시도 전체가 실패다.
- 검수 응답이 후보 목록·순서와 다르면 검수 실행 실패다. 검수 뒤 문구는 바꾸지 않는다(최종 카드는 원 후보의 사본).
"""
from __future__ import annotations

import re
import uuid
from typing import Any

from . import errors
from .resources import CATEGORIES, GATE_BOOLEANS, PERSONAL_KINDS, VIEWER_FACT_KINDS, package_config
from .util import iso, sha256_json

TITLE_SUFFIX = "볼까요?"
# 앱 추천 칸은 두 줄까지 보여 준다. 30자면 폭 좁은 폰에서도 두 줄 안에 든다(명세의 60자에서 줄임, 2026-11-09).
TITLE_MAX_CHARS: int = package_config()["generation"]["title_max_chars"]
NO_PERSONALIZATION = {"kind": "none", "source_quotes": [], "actor_ids": [], "delta": ""}


def _with_sha(body: dict[str, Any]) -> dict[str, Any]:
    return {**body, "input_sha256": sha256_json(body)}


def current_block(analysis_input: dict[str, Any], snapshot: dict[str, Any]) -> dict[str, Any]:
    return {"video_id": str(analysis_input["video_id"]), "author_id": str(analysis_input["author_id"]),
            "published_at": iso(analysis_input["published_at"]), "caption": analysis_input["caption"],
            "contextual_description": snapshot["contextual_description"]}


def generation_input(task_id: str, current: dict[str, Any], viewer_id: str, references: list[dict[str, Any]],
                     history_records: list[dict[str, Any]], personal_history: list[dict[str, Any]] | None = None,
                     base_cards: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    return _with_sha({"task_id": task_id, "current": current, "viewer_id": viewer_id, "categories": list(CATEGORIES),
                      "references": references, "history_records": history_records,
                      "personal_history": personal_history or [], "base_cards": base_cards or []})


def gate_input(task_id: str, current: dict[str, Any], viewer_id: str, references: list[dict[str, Any]],
               history_records: list[dict[str, Any]], personal_history: list[dict[str, Any]],
               base_cards: list[dict[str, Any]], candidates: list[dict[str, Any]]) -> dict[str, Any]:
    return _with_sha({"task_id": task_id, "current": current, "viewer_id": viewer_id, "references": references,
                      "history_records": history_records, "personal_history": personal_history,
                      "base_cards": base_cards, "candidates": candidates})


def candidate_number(candidate_id: str) -> int:
    return int(candidate_id[1:])


def _content_problem(candidate: dict[str, Any], payload: dict[str, Any], personal: bool,
                     viewer_id: str | None, uploader_id: str | None) -> str | None:
    if candidate["category"] not in payload["categories"]:
        return "category"
    title = candidate["title"]
    if not title.strip() or len(title) > TITLE_MAX_CHARS or not title.endswith(TITLE_SUFFIX):
        return "title"
    if not candidate["example"].strip() or len(candidate["example"]) > 120:
        return "example"
    anchor = candidate["current_anchor"]
    current = payload["current"]
    if not anchor.strip() or (anchor not in current["caption"] and anchor not in current["contextual_description"]):
        return "current_anchor"
    reference_ids = candidate["reference_ids"]
    if len(set(reference_ids)) != len(reference_ids) or not set(reference_ids) <= {r["reference_id"] for r in payload["references"]}:
        return "reference_ids"
    p = candidate["personalization"]
    if not personal:
        return None if p == NO_PERSONALIZATION else "generic_personalization"
    if p["kind"] not in PERSONAL_KINDS:
        return "personal_kind"
    if not p["delta"].strip() or len(p["delta"]) > 150:
        return "personal_delta"
    records = {r["source_id"]: r for r in payload["personal_history"]}
    quotes = p["source_quotes"]
    if not 1 <= len(quotes) <= 4 or len({(q["source_id"], q["quote"]) for q in quotes}) != len(quotes):
        return "personal_quote_count"
    for quote in quotes:
        record = records.get(quote["source_id"])
        if record is None or not quote["quote"].strip() or quote["quote"] not in record["text"]:
            return "personal_quote_not_exact"
    actors = p["actor_ids"]
    if not actors or not set(actors) <= {viewer_id, uploader_id}:
        return "personal_actor"
    if p["kind"] in VIEWER_FACT_KINDS:
        # 취향·경험·상황은 시청자 본인 기록에서만 나올 수 있다(타인 경험 전가 금지).
        if actors != [viewer_id] or not any(records[q["source_id"]]["role"] == "own" for q in quotes):
            return "personal_fact_not_viewer_owned"
    return None


def validate_candidates(output: dict[str, Any], payload: dict[str, Any], *, personal: bool,
                        viewer_id: str | None = None, uploader_id: str | None = None
                        ) -> tuple[list[dict[str, Any]], list[dict[str, str]]]:
    """(검수에 넘길 후보, 버린 후보와 이유). 출력 전체 계약 위반은 SEMANTIC_INVALID."""
    candidates = output["candidates"]
    prefix, cap = ("P", 6) if personal else ("G", 10)
    if len(candidates) > cap:
        raise errors.semantic(f"{len(candidates)} candidates > {cap}")
    ids = [c["candidate_id"] for c in candidates]
    if len(set(ids)) != len(ids) or not all(re.fullmatch(prefix + r"[1-9][0-9]*", i) for i in ids):
        raise errors.semantic("candidate ids must be unique " + prefix + "1…")
    if personal:
        if output["personalization_used"] != bool(candidates):
            raise errors.semantic("personalization_used contradicts candidates")
    elif output["personalization_used"]:
        raise errors.semantic("generic output claims personalization")
    kept, dropped = [], []
    for candidate in candidates:
        problem = _content_problem(candidate, payload, personal, viewer_id, uploader_id)
        if problem:
            dropped.append({"candidate_id": candidate["candidate_id"], "reason": problem})
        else:
            kept.append(candidate)
    return kept, dropped


def validate_gate(output: dict[str, Any], candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    assessments = output["assessments"]
    if [a["candidate_id"] for a in assessments] != [c["candidate_id"] for c in candidates]:
        raise errors.semantic("gate ids/order differ from candidates")
    for a in assessments:
        if not all(type(a[k]) is bool for k in (*GATE_BOOLEANS, "useful_personalization")):
            raise errors.semantic("gate booleans")
        if type(a["quality"]) is not int or not 0 <= a["quality"] <= 4:
            raise errors.semantic("gate quality")
        if not isinstance(a["redundancy_group"], str) or not a["redundancy_group"].strip():
            raise errors.semantic("gate redundancy_group")
    return assessments


def accepted_candidates(candidates: list[dict[str, Any]], assessments: list[dict[str, Any]], personal: bool = False
                        ) -> list[dict[str, Any]]:
    """8개 검사 통과·issue none·quality≥3(개인은 useful_personalization 도). quality 내림차순, 후보 순서 오름차순."""
    result = []
    for c, a in zip(candidates, assessments, strict=True):
        if all(a[k] for k in GATE_BOOLEANS) and a["issue"] == "none" and a["quality"] >= 3 \
                and (not personal or a["useful_personalization"]):
            result.append(c | {"quality": a["quality"], "redundancy_group": a["redundancy_group"]})
    return sorted(result, key=lambda x: (-x["quality"], candidate_number(x["candidate_id"])))


def select_generic(candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    selected: list[dict[str, Any]] = []
    for c in candidates:
        if len(selected) == 3:
            break
        if any(x["category"] == c["category"] or x["redundancy_group"] == c["redundancy_group"] for x in selected):
            continue
        selected.append(c)
    return selected


def merge_personal(base: list[dict[str, Any]], personal: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], bool]:
    """연구 merge_personal 과 같다. base 의 redundancy_group 은 'base:<card_id>'.

    같은 유형: 품질이 엄격히 높을 때만 교체(제자리). 다른 유형이 의미 중복이면 버림.
    빈 자리: 끝에 추가. 만석: 최저 품질보다 엄격히 높을 때만 교체, 최저 동률이면 뒤 순위 카드를 교체.
    """
    chosen = [dict(c) for c in base]
    for candidate in personal:
        if any((c["title"], c["example"]) == (candidate["title"], candidate["example"])
               or (c["category"] != candidate["category"] and c["redundancy_group"] == candidate["redundancy_group"])
               for c in chosen):
            continue
        same = [i for i, c in enumerate(chosen) if c["category"] == candidate["category"]]
        if same:
            i = same[0]
            if candidate["quality"] > chosen[i]["quality"]:
                chosen[i] = candidate
        elif len(chosen) < 3:
            chosen.append(candidate)
        else:
            i = min(range(len(chosen)), key=lambda j: (chosen[j]["quality"], -j))
            if candidate["quality"] > chosen[i]["quality"]:
                chosen[i] = candidate
    if len(chosen) > 3 or len({c["category"] for c in chosen}) != len(chosen):
        raise AssertionError("selection contract")
    used = any(c.get("personalization", {}).get("kind", "none") != "none" for c in chosen)
    return chosen, used


def new_card(candidate: dict[str, Any]) -> dict[str, Any]:
    """검수 통과 후보의 정확한 사본에 카드 id 만 붙인다."""
    return {"card_id": str(uuid.uuid4()), "candidate_id": candidate["candidate_id"], "category": candidate["category"],
            "title": candidate["title"], "example": candidate["example"], "current_anchor": candidate["current_anchor"],
            "reference_ids": candidate["reference_ids"], "personalization": candidate["personalization"],
            "quality": candidate["quality"], "redundancy_group": candidate["redundancy_group"]}


def public_cards(internal: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [{"card_id": c["card_id"], "category": c["category"], "title": c["title"]} for c in internal]


def base_cards_for_model(internal: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [{"card_id": c["card_id"], "category": c["category"], "title": c["title"], "example": c["example"]} for c in internal]

"""8장 선택 규칙과 7장 개인화 병합(연구 select_generic/merge_personal 과 같은 동작). Depth 꺼짐·실패 때(fail-open)의 선택이다."""
from reply_ai.generation import accepted_candidates, merge_personal, select_generic
from reply_ai.resources import GATE_BOOLEANS


def cand(cid, category, title=None, kind="none"):
    return {"candidate_id": cid, "category": category, "title": title or f"{cid} 물어볼까요?", "example": f"{cid} 예문",
            "current_anchor": "과제", "reference_ids": [], "personalization": {"kind": kind, "source_quotes": [], "actor_ids": [], "delta": ""}}


def gate(cid, quality, group, ok=True, issue="none", useful=False):
    return {"candidate_id": cid, **{k: ok for k in GATE_BOOLEANS}, "useful_personalization": useful, "quality": quality,
            "redundancy_group": group, "issue": issue, "reason": ""}


def test_gen01_no_good_candidate_gives_empty_set():
    """GEN01: 좋은 후보가 0개면 0개(예시 카드로 채우지 않는다)."""
    candidates = [cand("G1", "question"), cand("G2", "compliment"), cand("G3", "humor")]
    assessments = [gate("G1", 2, "a"), gate("G2", 4, "b", ok=False), gate("G3", 4, "c", issue="tone")]
    assert select_generic(accepted_candidates(candidates, assessments)) == []


def test_gen02_one_card_per_category_and_at_most_three():
    """GEN02: 같은 유형의 적절한 후보가 여러 개여도 유형당 1개, 전체 3개 이하."""
    candidates = [cand(f"G{i}", c) for i, c in enumerate(["question", "question", "support", "compliment", "empathy"], 1)]
    assessments = [gate(c["candidate_id"], 4 if i < 2 else 3, f"g{i}") for i, c in enumerate(candidates)]
    selected = select_generic(accepted_candidates(candidates, assessments))
    assert [c["candidate_id"] for c in selected] == ["G1", "G3", "G4"]
    assert len({c["category"] for c in selected}) == len(selected) == 3


def test_gen03_different_category_same_meaning_keeps_one():
    """GEN03: 유형이 달라도 의미 그룹이 같으면 하나만 남긴다."""
    candidates = [cand("G1", "question"), cand("G2", "empathy")]
    assessments = [gate("G1", 3, "deadline"), gate("G2", 4, "deadline")]
    selected = select_generic(accepted_candidates(candidates, assessments))
    assert [c["candidate_id"] for c in selected] == ["G2"]


def test_quality_then_candidate_order():
    candidates = [cand("G1", "question"), cand("G2", "support"), cand("G10", "humor")]
    assessments = [gate("G1", 3, "a"), gate("G2", 4, "b"), gate("G10", 3, "c")]
    assert [c["candidate_id"] for c in accepted_candidates(candidates, assessments)] == ["G2", "G1", "G10"]


def base_card(card_id, cid, category, quality):
    return {**cand(cid, category), "card_id": card_id, "quality": quality, "redundancy_group": "base:" + card_id}


def personal(cid, category, quality, group, title=None):
    return {**cand(cid, category, title=title, kind="past_experience"), "quality": quality, "redundancy_group": group}


def test_per01_no_personal_candidate_keeps_generic_set_exactly():
    """PER01: 채택되는 개인 후보가 없으면 일반 카드 ID·내용·순서를 그대로 유지."""
    base = [base_card("c1", "G1", "question", 3), base_card("c2", "G2", "support", 4)]
    chosen, used = merge_personal(base, [])
    assert not used and [c["card_id"] for c in chosen] == ["c1", "c2"] and chosen == base


def test_per02_equal_quality_does_not_replace():
    """PER02: 같은 유형·같은 품질이면 기존 카드를 지킨다."""
    base = [base_card("c1", "G1", "question", 3)]
    chosen, used = merge_personal(base, [personal("P1", "question", 3, "p-a")])
    assert not used and chosen[0]["card_id"] == "c1"


def test_strictly_higher_same_category_replaces_in_place():
    base = [base_card("c1", "G1", "question", 3), base_card("c2", "G2", "support", 3)]
    chosen, used = merge_personal(base, [personal("P1", "question", 4, "p-a")])
    assert used and [c.get("card_id", c["candidate_id"]) for c in chosen] == ["P1", "c2"]


def test_cross_category_redundant_with_base_is_rejected():
    base = [base_card("c1", "G1", "question", 3)]
    chosen, used = merge_personal(base, [personal("P1", "self_disclosure", 4, "base:c1")])
    assert not used and len(chosen) == 1


def test_empty_slot_appends_at_end():
    base = [base_card("c1", "G1", "question", 3)]
    chosen, used = merge_personal(base, [personal("P1", "self_disclosure", 3, "p-a")])
    assert used and [c.get("card_id", c["candidate_id"]) for c in chosen] == ["c1", "P1"]


def test_full_set_replaces_lowest_and_later_one_on_tie():
    """만석: 최저 품질보다 엄격히 높을 때만, 최저 동률이면 뒤 순위 카드를 교체(제자리)."""
    base = [base_card("c1", "G1", "question", 3), base_card("c2", "G2", "support", 3), base_card("c3", "G3", "empathy", 4)]
    chosen, used = merge_personal(base, [personal("P1", "self_disclosure", 4, "p-a")])
    assert used and [c.get("card_id", c["candidate_id"]) for c in chosen] == ["c1", "P1", "c3"]
    same, used_same = merge_personal(base, [personal("P2", "humor", 3, "p-b")])
    assert not used_same and [c["card_id"] for c in same] == ["c1", "c2", "c3"]

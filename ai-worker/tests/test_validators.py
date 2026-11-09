"""분석·후보·검수 의미 검사와 평가 칸 누출 방지."""
import pytest

from reply_ai import analysis, errors, generation
from reply_ai.resources import GATE_BOOLEANS, package_config, prompt_file, schema

from .conftest import T0, record


def payload(history=None):
    row = {"id": 1, "video_id": 5, "author_id": 9, "published_at": T0, "caption": "내일 발표", "history_records": history or []}
    frames = [{"frame_id": f"frame-{p}", "position_percent": p, "timestamp_ms": p * 30, "width": 720, "height": 1280,
               "sha256": f"s{p}"} for p in (10, 50, 90)]
    return analysis.model_input(row, frames, include_history=True)


def output(**kw):
    base = {"current_visual_description": "책상 위 노트북 화면에 발표 자료가 보인다.",
            "contextual_description": "책상 위 노트북 화면에 발표 자료가 보인다.", "history_requirement": "not_needed",
            "history_used": False, "history_note": "", "visual_facts": [{"fact_id": "F1", "text": "노트북", "frame_ids": ["frame-10"]}],
            "context_links": []}
    return {**base, **kw}


def test_analysis_ok_without_history():
    assert analysis.validate_output(output(), payload()) == set()


def test_hist03_required_without_evidence_keeps_current_description():
    """HIST03: 기록이 필요해 보여도 근거가 없으면 history_used=false 로 현재 설명을 유지한다."""
    assert analysis.validate_output(output(history_requirement="required"), payload()) == set()


def test_history_used_false_requires_identical_descriptions():
    with pytest.raises(errors.JobFailure) as info:
        analysis.validate_output(output(contextual_description="다른 설명"), payload())
    assert info.value.code == errors.SEMANTIC_INVALID


def test_hist01_quote_must_exist_in_its_source():
    """HIST01: 연결은 원문의 정확한 인용과 설명 구절을 가져야 한다."""
    history = [record("chat:1", "9", "내일 발표 준비해야 돼")]
    linked = output(history_requirement="required", history_used=True,
                    contextual_description="책상 위 노트북 화면에 발표 자료가 보인다. 전날 말한 발표 준비를 하는 것으로 보인다.",
                    context_links=[{"link_id": "L1", "kind": "conversational_continuation", "statement": "발표 준비",
                                    "description_span": "전날 말한 발표 준비를 하는 것으로 보인다.",
                                    "source_quotes": [{"source_id": "chat:1", "quote": "발표 준비해야 돼"}],
                                    "current_anchor": "발표", "strength": "explicit"}])
    assert analysis.validate_output(linked, payload(history)) == {"chat:1"}
    wrong = dict(linked, context_links=[dict(linked["context_links"][0], source_quotes=[{"source_id": "chat:1", "quote": "발표 끝났어"}])])
    with pytest.raises(errors.JobFailure):
        analysis.validate_output(wrong, payload(history))
    unknown = dict(linked, context_links=[dict(linked["context_links"][0], source_quotes=[{"source_id": "chat:99", "quote": "발표"}])])
    with pytest.raises(errors.JobFailure):
        analysis.validate_output(unknown, payload(history))


def test_not_needed_cannot_use_history():
    history = [record("chat:1", "9", "발표")]
    bad = output(history_used=True, contextual_description="책상 위 노트북 화면에 발표 자료가 보인다. 발표",
                 context_links=[{"link_id": "L1", "kind": "background_fact", "statement": "s", "description_span": "발표",
                                 "source_quotes": [{"source_id": "chat:1", "quote": "발표"}], "current_anchor": "발표", "strength": "strong"}])
    with pytest.raises(errors.JobFailure):
        analysis.validate_output(bad, payload(history))


def test_eval01_evaluation_fields_are_rejected_before_model_call():
    """EVAL01: 생성 입력에 평가용 실제 답장 칸이 들어가면 스키마와 누출 검사가 막는다."""
    gen = generation.generation_input("t", {"video_id": "5", "author_id": "9", "published_at": "2026-11-08T03:00:00Z",
                                            "caption": "c", "contextual_description": "d"}, "generic", [], [])
    analysis.validate_input("generation-input.schema.json", gen)
    with pytest.raises(errors.JobFailure):
        analysis.validate_input("generation-input.schema.json", {**gen, "actual_reply": "정답"})
    leaked = dict(gen, references=[{"reference_id": "R1", "caption": "c", "current_visual_description": "d",
                                    "replies": [{"reply_id": "R1-1", "text": "t", "actual_reply": "x"}]}])
    with pytest.raises(errors.JobFailure):
        analysis.validate_input("generation-input.schema.json", leaked)


def gen_payload(personal_history=None):
    return generation.generation_input("t", {"video_id": "5", "author_id": "9", "published_at": "2026-11-08T03:00:00Z",
                                             "caption": "과제 마감 D-1", "contextual_description": "책상에서 과제를 하고 있다."},
                                       "3", [{"reference_id": "R1", "caption": "c", "current_visual_description": "d",
                                              "replies": [{"reply_id": "R1-1", "text": "화이팅"}]}], [],
                                       personal_history=personal_history)


def candidate(cid="G1", **kw):
    base = {"candidate_id": cid, "category": "question", "title": "무슨 과제인지 물어볼까요?", "example": "무슨 과제야?",
            "current_anchor": "과제", "reference_ids": ["R1"], "personalization": dict(generation.NO_PERSONALIZATION)}
    return {**base, **kw}


def test_candidate_content_rules_drop_only_bad_candidates():
    out = {"candidates": [candidate(), candidate("G2", title="과제 화이팅"), candidate("G3", current_anchor="없는 문장"),
                          candidate("G4", reference_ids=["R9"])], "personalization_used": False, "reason": ""}
    kept, dropped = generation.validate_candidates(out, gen_payload(), personal=False)
    assert [c["candidate_id"] for c in kept] == ["G1"]
    assert {d["reason"] for d in dropped} == {"title", "current_anchor", "reference_ids"}


def test_title_over_30_chars_is_dropped_so_the_card_fits_two_lines():
    fits, too_long = "가" * 26 + "볼까요?", "가" * 27 + "볼까요?"
    assert (len(fits), len(too_long)) == (30, 31)
    out = {"candidates": [candidate(title=fits), candidate("G2", title=too_long)], "personalization_used": False, "reason": ""}
    kept, dropped = generation.validate_candidates(out, gen_payload(), personal=False)
    assert [c["candidate_id"] for c in kept] == ["G1"]
    assert [(d["candidate_id"], d["reason"]) for d in dropped] == [("G2", "title")]


def test_title_limit_is_the_same_30_in_prompts_schemas_config_and_check():
    # 앱 추천 칸은 두 줄까지(폭 좁은 폰 기준 30자면 늘 두 줄 안) — 지시·출력 형식·설정·코드 검사가 같은 값을 쓴다
    limit = 30
    assert generation.TITLE_MAX_CHARS == limit
    assert package_config()["generation"]["title_max_chars"] == limit
    for name in ("candidates.schema.json", "gate-input.schema.json"):
        assert schema(name)["properties"]["candidates"]["items"]["properties"]["title"]["maxLength"] == limit, name
    for name in ("02-generic.txt", "03-personal.txt"):
        assert f"{limit}자 이하" in prompt_file(name) and "60자" not in prompt_file(name), name


def test_generic_candidate_cannot_carry_personal_fact():
    p = {"kind": "past_experience", "source_quotes": [{"source_id": "chat:1", "quote": "x"}], "actor_ids": ["3"], "delta": "d"}
    kept, dropped = generation.validate_candidates({"candidates": [candidate(personalization=p)], "personalization_used": False,
                                                    "reason": ""}, gen_payload(), personal=False)
    assert kept == [] and dropped[0]["reason"] == "generic_personalization"


def test_duplicate_ids_fail_whole_output():
    with pytest.raises(errors.JobFailure):
        generation.validate_candidates({"candidates": [candidate(), candidate()], "personalization_used": False, "reason": ""},
                                       gen_payload(), personal=False)


def test_personal_fact_must_come_from_viewer_own_record():
    """타인의 경험을 시청자 경험으로 제안하지 않는다(PER01 근거 규칙)."""
    history = [record("chat:1", "3", "나도 어제 과제 밤샜어", role="own"), record("chat:2", "8", "나 발표 망했어", role="parent_context")]
    good = candidate("P1", personalization={"kind": "past_experience", "source_quotes": [{"source_id": "chat:1", "quote": "어제 과제 밤샜어"}],
                                            "actor_ids": ["3"], "delta": "밤샌 경험을 꺼낸다"})
    other = candidate("P2", personalization={"kind": "past_experience", "source_quotes": [{"source_id": "chat:2", "quote": "발표 망했어"}],
                                             "actor_ids": ["3"], "delta": "타인 경험"})
    kept, dropped = generation.validate_candidates({"candidates": [good, other], "personalization_used": True, "reason": ""},
                                                   gen_payload(history), personal=True, viewer_id="3", uploader_id="9")
    assert [c["candidate_id"] for c in kept] == ["P1"] and dropped[0]["reason"] == "personal_fact_not_viewer_owned"


def assessment(cid, **kw):
    return {"candidate_id": cid, **{k: True for k in GATE_BOOLEANS}, "useful_personalization": False, "quality": 3,
            "redundancy_group": "g-" + cid, "issue": "none", "reason": "", **kw}


def test_async03_gate_with_missing_or_reordered_ids_fails():
    """ASYNC03/8장: 검수 응답의 누락·중복·순서 변경은 검수 실행 실패."""
    kept = [candidate("G1"), candidate("G2")]
    assert len(generation.validate_gate({"assessments": [assessment("G1"), assessment("G2")]}, kept)) == 2
    for bad in ([assessment("G1")], [assessment("G2"), assessment("G1")], [assessment("G1"), assessment("G1")]):
        with pytest.raises(errors.JobFailure):
            generation.validate_gate({"assessments": bad}, kept)

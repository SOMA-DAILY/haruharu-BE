"""질문 짝 카드 규칙(최대 2장: 질문 1장 + 질문이 아닌 유형 1장, 같은 어미 금지, 유형별 제목 문형)과 버전 등록(use-package).

이전 카드 규칙(최대 3장, '볼까요?' 제목) 버전의 동작은 test_depth·test_selection 이 그대로 확인한다.
"""
from __future__ import annotations

import uuid

import pytest

from reply_ai import depth, generation, jobs, versions
from reply_ai.db import jsonb
from reply_ai.provider import ScriptedProvider
from reply_ai.resources import package_config, prompt_file

from .conftest import record
from .test_depth import FakeDepthModel, depth_server, translate  # noqa: F401 (fixture)
from .test_pipeline import FakeFetcher, TITLES, analysis_output, candidates_output, gate_output, legacy_config, start, worker
from .test_validators import candidate, gen_payload

QP = generation.card_policy(package_config())


def c(cid, category, title, *, group=None, quality=4, kind="none"):
    return {"candidate_id": cid, "category": category, "title": title, "example": f"{cid} 예문", "quality": quality,
            "redundancy_group": group or "grp-" + cid, "personalization": {"kind": kind}}


def ids(cards):
    return [x["candidate_id"] for x in cards]


# ───────────────────────── 규칙·설정 ─────────────────────────

def test_package_config_is_question_pair_with_two_cards_and_old_config_keeps_three():
    assert QP == generation.CardPolicy(max_cards=2, question_pair=True) and QP.title_suffix is None
    assert package_config()["generation"]["final_max_cards"] == 2
    assert generation.card_policy(legacy_config()) == generation.LEGACY_POLICY == generation.CardPolicy(3, False)
    assert generation.card_policy(None).title_suffix == "볼까요?"


def test_same_ending_is_last_two_chars_after_trailing_marks():
    assert generation.TITLE_ENDING_CHARS == 2
    assert generation.title_ending("어떤 과제인지 물어보기") == generation.title_ending("같이 해보기 ") == "보기"
    assert generation.title_ending("나도 발표 준비 중이라고 말하기") == generation.title_ending("밤새는 마음에 공감하기!") == "하기"
    assert generation.title_ending("무슨 과제인지 물어볼까요?") == "까요"


def test_prompts_use_per_category_title_forms_without_forced_suffix():
    for name in ("02-generic.txt", "03-personal.txt"):
        text = prompt_file(name)
        assert "'볼까요?'로 끝" not in text and "물어보기" in text and "말하기" in text, name
    gate = prompt_file("04-gate.txt")
    assert "같은 어미" in gate and "질문 1장" in gate and depth.gate_supports_pool(gate)
    assert "공감하기" in prompt_file("02-generic.txt")


def test_title_check_keeps_length_and_blank_rules_but_not_the_old_suffix():
    out = {"candidates": [candidate(title="어떤 과제인지 물어보기"), candidate("G2", title="가" * 31),
                          candidate("G3", title="  ")], "personalization_used": False, "reason": ""}
    kept, dropped = generation.validate_candidates(out, gen_payload(), personal=False, policy=QP)
    assert ids(kept) == ["G1"] and [(d["candidate_id"], d["reason"]) for d in dropped] == [("G2", "title"), ("G3", "title")]
    legacy_kept, _ = generation.validate_candidates(out, gen_payload(), personal=False)   # 이전 규칙은 '볼까요?' 를 요구
    assert legacy_kept == []


# ───────────────────────── 선택(질문 짝) ─────────────────────────

def test_at_most_two_cards_one_question_one_other_each_in_given_order():
    ordered = [c("G2", "support", "마감까지 응원 보내기"), c("G3", "empathy", "밤새는 마음에 공감하기"),
               c("G1", "question", "어떤 과제인지 물어보기"), c("G4", "compliment", "노트 칭찬해 주기")]
    selected = generation.select_generic(ordered, QP)
    assert ids(selected) == ["G2", "G1"]          # 비질문 자리는 맨 앞 G2, 질문 자리는 순서가 늦어도 G1. 표시는 주어진 순서
    assert ids(generation.select_generic(ordered)) == ["G2", "G3", "G1"]   # 이전 규칙(3장)은 그대로


def test_missing_question_fills_with_next_other_and_missing_other_leaves_one_question():
    no_question = [c("G2", "support", "마감까지 응원 보내기"), c("G3", "empathy", "밤새는 마음에 공감하기"),
                   c("G4", "compliment", "노트 칭찬해 주기")]
    assert ids(generation.select_generic(no_question, QP)) == ["G2", "G3"]
    only_questions = [c("G1", "question", "어떤 과제인지 물어보기"), c("G5", "question", "언제 끝나는지 여쭙기")]
    assert ids(generation.select_generic(only_questions, QP)) == ["G1"]   # 유형당 1개는 유지
    same_group = [c("G1", "question", "어떤 과제인지 물어보기", group="g"), c("G2", "support", "응원 보내기", group="g"),
                  c("G3", "empathy", "밤새는 마음에 공감하기")]
    assert ids(generation.select_generic(same_group, QP)) == ["G1", "G3"]  # 의미 그룹당 1개도 유지


def test_same_title_ending_is_blocked_and_replaced_by_next_candidate():
    ordered = [c("G1", "question", "어떤 과제인지 물어보기"), c("G2", "self_disclosure", "나도 같이 해보기"),
               c("G3", "empathy", "밤새는 마음에 공감하기")]
    assert ids(generation.select_generic(ordered, QP)) == ["G1", "G3"]    # G2 는 G1 과 같은 어미 '보기'
    fill = [c("G2", "support", "힘내라고 응원하기"), c("G3", "empathy", "밤새는 마음에 공감하기"),
            c("G4", "compliment", "노트 칭찬해 주기")]
    assert ids(generation.select_generic(fill, QP)) == ["G2", "G4"]       # 빈 자리 채우기도 같은 어미를 건너뛴다


def test_fail_open_applies_same_combination_in_quality_order():
    accepted = generation.accepted_candidates(
        [c("G1", "support", "마감까지 응원 보내기"), c("G2", "empathy", "밤새는 마음에 공감하기"),
         c("G3", "question", "어떤 과제인지 물어보기")],
        [gate_output([cid], quality=q)["assessments"][0] for cid, q in (("G1", 4), ("G2", 4), ("G3", 3))])
    assert ids(accepted) == ["G1", "G2", "G3"]                              # 품질순(기존 정렬)
    assert ids(depth.select_pool(accepted, policy=QP)) == ["G1", "G3"] == ids(generation.select_generic(accepted, QP))


def test_personal_candidate_takes_slots_by_depth_in_one_pool():
    """일반 G1(질문 0.77)·G2(응원 0.70)·G3(공감 0.60) + 개인 P1(자기 이야기 0.85)·P2(질문 0.82): 개인 두 장이 두 자리를 차지."""
    pool = [c("G1", "question", "무슨 과제인지 물어보기"), c("G2", "support", "마감까지 응원 보내기"),
            c("G3", "empathy", "밤새는 피곤함에 공감하기"), c("P1", "self_disclosure", "나도 밤샌 이야기 꺼내기", quality=3),
            c("P2", "question", "몇 시에 끝났는지 여쭙기", quality=3)]
    scores = {"G1": 0.77, "G2": 0.70, "G3": 0.60, "P1": 0.85, "P2": 0.82}
    assert ids(depth.select_pool(pool, scores, policy=QP)) == ["P1", "P2"]
    assert ids(depth.select_pool(pool, {**scores, "P2": 0.5}, policy=QP)) == ["P1", "G1"]


def test_merge_pool_quality_order_prefers_generic_card_on_equal_quality():
    base = [{**c("G1", "question", "무슨 과제인지 물어보기", group="base:c1", quality=3), "card_id": "c1"},
            {**c("G2", "support", "마감까지 응원 보내기", group="base:c2", quality=3), "card_id": "c2"}]
    higher = [c("P1", "self_disclosure", "나도 밤샌 이야기 꺼내기", kind="past_experience")]
    chosen, used = generation.merge_pool(base, higher, QP)
    assert ids(chosen) == ["P1", "G1"] and used
    chosen, used = generation.merge_pool(base, [{**higher[0], "quality": 3}], QP)
    assert ids(chosen) == ["G1", "G2"] and not used                          # 같은 품질이면 일반 카드가 앞선다


# ───────────────────────── 작업기 흐름(PostgreSQL, 패키지 버전) ─────────────────────────

def _provider(n=3, quality=4):
    return ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(n)],
                             "gate": [gate_output([f"G{i}" for i in range(1, n + 1)], quality=quality)],
                             "depth_translate": translate})


def _personal_candidate(cid, category, title, example):
    return {"candidate_id": cid, "category": category, "title": title, "example": example, "current_anchor": "과제 마감",
            "reference_ids": [], "personalization": {"kind": "past_experience",
                                                     "source_quotes": [{"source_id": "chat:9", "quote": "과제 하느라 밤샜어요"}],
                                                     "actor_ids": ["3"], "delta": "시청자의 밤샘 경험을 연결"}}


PERSONAL = [_personal_candidate("P1", "self_disclosure", "나도 밤샌 이야기 꺼내기", "저도 어제 과제 하느라 밤샜어요"),
            _personal_candidate("P2", "question", "몇 시에 끝났는지 여쭙기", "저도 밤샜는데 몇 시에 끝났어요?")]
SCORES = {"무슨 과제": 0.77, "화이팅": 0.70, "밤새면": 0.60, "밤샜어요": 0.85, "몇 시에": 0.82}


def _run(conn, database_url, sample_video, depth_server, *, enabled=True, scores=SCORES, personal=PERSONAL,
         before_personal=None, legacy=False):
    model = FakeDepthModel(scores)
    provider = _provider()
    start(conn, legacy=legacy)
    w = worker(database_url, provider, FakeFetcher(sample_video), depth_ranking_enabled=enabled, depth_url=depth_server(model))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    base = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    if before_personal:
        before_personal(conn)
    records = [record("chat:9", "3", "저도 어제 과제 하느라 밤샜어요", role="own")]
    pinput = conn.execute(
        """INSERT INTO ai_reply.personal_input (viewer_id, video_id, analysis_id, base_set_id, personal_records,
               personal_snapshot_hash, version_id) VALUES (3, 101, %s, %s, %s, %s, %s) RETURNING id""",
        (base["analysis_id"], base["set_id"], jsonb(records), uuid.uuid4().hex, versions.active(conn)["version_id"])).fetchone()
    jobs.enqueue(conn, f"personal:{pinput['id']}", "PERSONAL", {"personal_input_id": pinput["id"]})
    provider.responses["personal"] = [{"candidates": personal, "personalization_used": True, "reason": "개인 기록"}]
    provider.responses["gate"] = [gate_output([p["candidate_id"] for p in personal], quality=3, useful=True)]
    w.run_once(conn, "t")
    personal_set = conn.execute("SELECT * FROM ai_reply.recommendation_set WHERE kind = 'PERSONAL'").fetchone()
    attempt = conn.execute("SELECT * FROM ai_reply.generation_attempt WHERE stage = 'PERSONAL'").fetchone()
    return base, personal_set, attempt, provider


@pytest.mark.pg
def test_generic_set_two_cards_question_slot_and_display_by_depth(conn, database_url, sample_video, depth_server):
    model = FakeDepthModel({"밤새면": 0.9, "화이팅": 0.5, "무슨 과제": 0.1})
    start(conn)
    w = worker(database_url, _provider(), FakeFetcher(sample_video), depth_ranking_enabled=True,
               depth_url=depth_server(model))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    rset = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    assert [c["category"] for c in rset["cards"]] == ["empathy", "question"]      # 질문은 Depth 가 가장 낮아도 질문 자리
    assert [c["title"] for c in rset["cards"]] == [TITLES[2], TITLES[0]]
    assert [c["depth"]["rank"] for c in rset["internal_cards"]] == [1, 2]
    ranking = conn.execute("SELECT candidates FROM ai_reply.generation_attempt").fetchone()["candidates"]["depth_ranking"]
    assert ranking["selection_policy"] == depth.SELECTION_POLICY_QUESTION_PAIR
    assert ranking["selected"] == ["G3", "G1"] and ranking["baseline_selected"] == ["G1", "G2"]


@pytest.mark.pg
def test_generic_set_off_is_two_cards_in_quality_order(conn, database_url, sample_video, depth_server):
    model = FakeDepthModel({})
    start(conn)
    w = worker(database_url, _provider(), FakeFetcher(sample_video), depth_url=depth_server(model))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    rset = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    assert [c["category"] for c in rset["cards"]] == ["question", "support"] and model.requests == []


@pytest.mark.pg
def test_personal_candidates_take_both_slots_by_depth(conn, database_url, sample_video, depth_server):
    """개인 P1 0.85·P2(질문) 0.82 > 일반 질문 0.77: 개인 후보가 두 자리를 모두 차지한다(품질 3 < 일반 4 라도)."""
    base, personal, attempt, provider = _run(conn, database_url, sample_video, depth_server)
    assert [c["candidate_id"] for c in base["internal_cards"]] == ["G1", "G2"]
    assert [c["candidate_id"] for c in personal["internal_cards"]] == ["P1", "P2"]
    assert [c["depth"]["score"] for c in personal["internal_cards"]] == [0.85, 0.82]
    assert [c["title"] for c in personal["cards"]] == ["나도 밤샌 이야기 꺼내기", "몇 시에 끝났는지 여쭙기"]
    ranking = attempt["candidates"]["depth_ranking"]
    assert ranking["status"] == "OK" and ranking["selection_policy"] == depth.SELECTION_POLICY_QUESTION_PAIR
    gate_payload = [c for c in provider.calls if c.stage == "gate"][-1].payload
    assert gate_payload["base_cards"] == [] and len(gate_payload["generic_candidates"]) == 3


@pytest.mark.pg
def test_personal_lower_question_keeps_generic_question_card(conn, database_url, sample_video, depth_server):
    base, personal, _, _ = _run(conn, database_url, sample_video, depth_server, scores={**SCORES, "몇 시에": 0.5})
    g1 = next(c for c in base["internal_cards"] if c["candidate_id"] == "G1")
    assert [c["candidate_id"] for c in personal["internal_cards"]] == ["P1", "G1"]
    assert personal["internal_cards"][1]["card_id"] == g1["card_id"]


@pytest.mark.pg
def test_personal_depth_off_uses_same_combination_in_quality_order(conn, database_url, sample_video, depth_server):
    """Depth 꺼짐: 일반 카드(품질 4) + 개인 후보(품질 3) 한 풀 품질순 → 일반 카드가 그대로라 개인 세트 없음."""
    base, personal, attempt, provider = _run(conn, database_url, sample_video, depth_server, enabled=False)
    assert personal is None and attempt["result_set_id"] == base["set_id"]
    gate_payload = [c for c in provider.calls if c.stage == "gate"][-1].payload
    assert "generic_candidates" not in gate_payload and len(gate_payload["base_cards"]) == 2


@pytest.mark.pg
def test_old_gate_prompt_version_does_not_use_one_pool(conn, database_url, sample_video, depth_server):
    """활성 버전 검수 지시문이 그룹 재사용(generic_candidates)을 모르면 Depth 켜짐이어도 꺼짐 경로(base_cards·품질순)를 쓴다."""
    def old_gate(conn):
        current = versions.active(conn)
        prompts = {**current["prompts_json"], "gate": "의미상 같은 후보는 같은 redundancy_group 을 쓴다. base:<card_id> 로 표시한다."}
        row = versions.build_version("oldgate", current["config_json"], prompts, current["gallery_snapshot_id"],
                                     parent=current["version_id"])
        versions.insert_version(conn, row)
        versions.activate(conn, row["version_id"], current["version_id"], current["revision"], "test old gate")

    base, personal, attempt, provider = _run(conn, database_url, sample_video, depth_server, before_personal=old_gate)
    gate_payload = [c for c in provider.calls if c.stage == "gate"][-1].payload
    assert "generic_candidates" not in gate_payload and len(gate_payload["base_cards"]) == 2
    assert attempt["candidates"]["depth_ranking"] == {"status": depth.POOL_UNAVAILABLE,
                                                      "detail": "gate prompt without generic_candidates"}
    assert personal is None and attempt["result_set_id"] == base["set_id"]   # 품질순: 일반 카드(품질 4)가 그대로


@pytest.mark.pg
def test_empty_generic_pool_falls_back_to_off_path(conn, database_url, sample_video, depth_server):
    def drop_gate_record(conn):
        conn.execute("UPDATE ai_reply.generation_attempt SET gate = NULL WHERE stage = 'GENERIC'")

    _, _, attempt, provider = _run(conn, database_url, sample_video, depth_server, before_personal=drop_gate_record)
    gate_payload = [c for c in provider.calls if c.stage == "gate"][-1].payload
    assert "generic_candidates" not in gate_payload and len(gate_payload["base_cards"]) == 2
    assert attempt["candidates"]["depth_ranking"]["detail"] == "no generic accepted candidates"


# ───────────────────────── 버전 등록 ─────────────────────────

@pytest.mark.pg
def test_use_package_registers_and_activates_current_prompts_and_config(conn):
    start(conn, legacy=True)
    old = versions.active(conn)
    assert generation.card_policy(old["config_json"]) == generation.LEGACY_POLICY
    result = versions.use_package(conn, "질문 짝 카드 규칙")
    current = versions.active(conn)
    assert result["changed"] and result["from"] == old["version_id"] and current["version_id"] == result["version_id"]
    assert generation.card_policy(current["config_json"]) == QP and current["parent_version_id"] == old["version_id"]
    assert current["gallery_snapshot_id"] == old["gallery_snapshot_id"] and current["revision"] == old["revision"] + 1
    assert depth.gate_supports_pool(current["prompts_json"]["gate"])
    assert versions.use_package(conn, "again") == {"changed": False, "version_id": current["version_id"],
                                                   "revision": current["revision"]}


@pytest.mark.pg
def test_legacy_version_keeps_three_cards_with_old_titles(conn, database_url, sample_video):
    start(conn, legacy=True)
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(3, legacy=True)],
                                 "gate": [gate_output(["G1", "G2", "G3"], quality=4)]})
    w = worker(database_url, provider, FakeFetcher(sample_video))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    rset = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    assert [c["category"] for c in rset["cards"]] == ["question", "support", "empathy"]

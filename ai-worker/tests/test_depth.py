"""Depth 평가 모델 랭킹: 토큰 배치·점수 위치(서비스), 정렬·fail-open·꺼짐=기존 동작(작업기).

작업기 흐름(PostgreSQL) 테스트는 질문 짝 카드 규칙 이전 버전(최대 3장, '볼까요?' 제목)을 활성화해 그 버전의 동작이
그대로인지 본다. 질문 짝 규칙(최대 2장)은 test_card_policy.py.
"""
from __future__ import annotations

import threading
import uuid
from http.server import ThreadingHTTPServer

import httpx
import pytest

from reply_ai import analysis as analysis_mod
from reply_ai import depth, errors, generation, jobs, pipeline, versions
from reply_ai.db import jsonb
from reply_ai.config import Settings
from reply_ai.depth_service import EOS_ID, _valid_request, build_ids, layout, make_handler, pad_batch
from reply_ai.provider import ScriptedProvider

from .conftest import record
from .test_selection import cand, gate
from .test_pipeline import FakeFetcher, analysis_output, candidates_output, gate_output, personal_candidate, start, worker


# ───────────────────────── 서비스: 입력 만들기 ─────────────────────────

def fake_encode(text: str) -> list[int]:
    """단어 하나 = 토큰 하나(단어 길이를 id 로)."""
    return [len(w) for w in text.split()]


def test_build_ids_ends_each_context_turn_with_eos_then_reply():
    ids = build_ids(fake_encode, ["a bb", "ccc"], "dddd e", max_seq_len=50)
    assert ids == [1, 2, EOS_ID, 3, EOS_ID, 4, 1]
    assert ids[-1] == fake_encode("dddd e")[-1]  # 마지막은 답장 마지막 토큰


def test_build_ids_truncates_context_from_the_left_to_max_seq_len():
    context = [" ".join(["x"] * 40), " ".join(["yy"] * 15)]
    reply = " ".join(["zzz"] * 5)
    ids = build_ids(fake_encode, context, reply, max_seq_len=50, context_cap=0)
    assert len(ids) == 50
    assert ids[-5:] == [3] * 5 and ids[-6] == EOS_ID          # 답장·구분자는 그대로
    assert ids[:28] == [1] * 28 and ids[28] == EOS_ID         # 전체 62개 중 맨 앞 맥락 12개가 잘렸다
    assert build_ids(fake_encode, context, reply, max_seq_len=0, context_cap=0) == \
        [1] * 40 + [EOS_ID] + [2] * 15 + [EOS_ID] + [3] * 5   # 0 이면 자르지 않는다


def test_context_capped_at_25_tokens_from_the_left_and_reply_kept():
    context = [" ".join(["x"] * 40), " ".join(["yy"] * 15)]  # C = 57 토큰(구분자 포함)
    reply = " ".join(["zzz"] * 20)
    ids, info = layout(fake_encode, context, reply, 50)       # 기본 상한 25
    assert ids == [1] * 8 + [EOS_ID] + [2] * 15 + [EOS_ID] + [3] * 20   # C 는 마지막 25 토큰, 답장 20 토큰 전부
    assert info == {"context_tokens": 57, "context_used_tokens": 25, "reply_tokens": 20, "input_tokens": 77,
                    "used_tokens": 45, "truncated": True, "context_truncated": True, "reply_truncated": False}
    assert build_ids(fake_encode, context, reply, 50) == ids
    short_ids, short = layout(fake_encode, ["a bb"], "ccc", 50)
    assert short_ids == [1, 2, EOS_ID, 3] and not short["truncated"]  # 짧은 C 는 상한에 걸리지 않는다


def test_long_reply_keeps_reply_and_cuts_context_further():
    context = [" ".join(["x"] * 40)]
    reply = " ".join(["zzz"] * 40)                            # C 25 + 답장 40 > 50 → C 를 10 으로
    ids, info = layout(fake_encode, context, reply, 50)
    assert ids == [1] * 9 + [EOS_ID] + [3] * 40 and info["context_used_tokens"] == 10 and not info["reply_truncated"]
    alone, extreme = layout(fake_encode, context, " ".join(["ab"] * 30 + ["c"] * 30), 50)   # 답장 혼자 60 > 50
    assert alone == [2] * 30 + [1] * 20 and extreme["reply_truncated"] and extreme["context_used_tokens"] == 0


def test_pad_batch_right_pads_and_points_at_each_reply_last_token():
    ids, mask, last = pad_batch([[5, EOS_ID, 7, 8], [5, EOS_ID, 9]])
    assert ids == [[5, EOS_ID, 7, 8], [5, EOS_ID, 9, EOS_ID]]
    assert mask == [[1, 1, 1, 1], [1, 1, 1, 0]]
    assert last == [3, 2]                                     # 첫 EOS(맥락 끝) 위치 1 이 아니다


def test_score_request_validation():
    assert _valid_request({"context_en": "hi", "candidates_en": ["a", "b"]}) == (["hi"], ["a", "b"])
    assert _valid_request({"context_en": [], "candidates_en": ["a"]}) == ([], ["a"])
    assert _valid_request({"context_en": ["hi"], "candidates_en": []}) is None
    assert _valid_request({"context_en": ["hi"], "candidates_en": [" "]}) is None
    assert _valid_request({"context_en": [1], "candidates_en": ["a"]}) is None
    assert _valid_request({"context_en": ["hi"], "candidates_en": ["a"] * 17}) is None
    many = [f"turn {i}" for i in range(40)]                     # 32턴을 넘어도 거절하지 않고 최근 32턴만 쓴다
    assert _valid_request({"context_en": many, "candidates_en": ["a"]}) == (many[-32:], ["a"])


def test_score_uses_reply_last_token_not_classification_head_pad_position():
    """작은 무작위 GPT-2 로: 직접 계산 점수 = 패딩 없이 한 줄씩 계산한 마지막 토큰 logit, 헤드 자동 위치와는 다르다."""
    torch = pytest.importorskip("torch")
    transformers = pytest.importorskip("transformers")
    from reply_ai.depth_service import DepthModel, sigmoid

    torch.manual_seed(0)
    config = transformers.GPT2Config(vocab_size=50257, n_positions=64, n_embd=32, n_layer=2, n_head=2, num_labels=1,
                                     pad_token_id=EOS_ID)
    model = DepthModel.__new__(DepthModel)
    model.torch, model.device, model.max_seq_len, model.context_cap = torch, "cpu", 50, 25
    model.model = transformers.GPT2ForSequenceClassification(config).eval()
    model._lock = threading.Lock()
    model._encode = lambda text: [ord(ch) for ch in text]
    context, replies = ["hi"], ["yes!", "no"]
    got = model.score(context, replies)
    for reply, value in zip(replies, got):
        ids = torch.tensor([build_ids(model._encode, context, reply, 50)])
        with torch.no_grad():
            logits = model.model.score(model.model.transformer(input_ids=ids).last_hidden_state)[0, :, 0]
        assert value == pytest.approx(sigmoid(float(logits[-1])), abs=1e-5)
        assert value != pytest.approx(sigmoid(float(logits[len(context[0]) - 1])), abs=1e-5)  # 맥락 끝 위치가 아니다


# ───────────────────────── 작업기: 정렬·클라이언트 ─────────────────────────

def test_rank_by_scores_descending_and_ties_keep_original_order():
    cards = [{"card_id": c} for c in "abcd"]
    assert [c["card_id"] for c in depth.rank_by_scores(cards, [0.1, 0.5, 0.1, 0.9])] == ["d", "b", "a", "c"]


def _client(handler) -> depth.DepthClient:
    return depth.DepthClient(Settings(depth_url="http://depth.test"), transport=httpx.MockTransport(handler))


def test_client_rejects_wrong_count_non_finite_and_http_errors():
    ok = {"scores": [0.2, 0.1], "model_id": "m", "model_revision": "r", "max_seq_len": 50}
    assert _client(lambda r: httpx.Response(200, json=ok)).score(["c"], ["a", "b"]).scores == [0.2, 0.1]
    for body, status in [({**ok, "scores": [0.2]}, 200), ({**ok, "scores": [0.2, float("nan")]}, 200),
                         ({**ok, "model_id": ""}, 200), ({"error": "x"}, 500)]:
        with pytest.raises(depth.DepthUnavailable):
            _client(lambda r, b=body, s=status: httpx.Response(s, json=b)).score(["c"], ["a", "b"])

    def refuse(request):
        raise httpx.ConnectError("refused", request=request)

    with pytest.raises(depth.DepthUnavailable) as caught:
        _client(refuse).info()
    assert caught.value.status == depth.SERVICE_UNAVAILABLE


# ───────────────────────── 작업기 흐름(PostgreSQL) ─────────────────────────

class FakeDepthModel:
    """영어 예문에 들어 있는 단어로 점수를 정한다. 받은 요청을 기록한다."""

    def __init__(self, scores: dict[str, float]):
        self.scores = scores
        self.requests: list[tuple[list[str], list[str]]] = []
        self.info = {"model_id": "fake-depth#len50", "model_revision": "0" * 40, "max_seq_len": 50}

    def inputs(self, context, replies):
        n = sum(len(t.split()) + 1 for t in context)
        return [{"context_tokens": n, "reply_tokens": len(r.split()), "input_tokens": n + len(r.split()),
                 "used_tokens": min(50, n + len(r.split())), "truncated": n + len(r.split()) > 50, "reply_truncated": False}
                for r in replies]

    def score(self, context, replies):
        self.requests.append((list(context), list(replies)))
        return [next((v for k, v in self.scores.items() if k in r), 0.0) for r in replies]


@pytest.fixture()
def depth_server():
    servers = []

    def run(model):
        server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(model))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        servers.append(server)
        return f"http://127.0.0.1:{server.server_address[1]}"

    yield run
    for server in servers:
        server.shutdown()
        server.server_close()


def translate(request):
    """압축 C 대역: 턴 id 를 이어 붙인 짧은 영어 한 줄. 예문은 'EN ' + 원문."""
    p = request.payload
    return {"context_en": ("EN C " + "+".join(t["turn_id"] for t in p["context_turns"])) if p["context_turns"] else "",
            "candidates": [{"card_id": c["card_id"], "text_en": "EN " + c["text"]} for c in p["candidates"]]}


def run_generic(conn, database_url, sample_video, provider, **settings):
    start(conn, legacy=True)
    w = worker(database_url, provider, FakeFetcher(sample_video), **settings)
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    rset = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    attempt = conn.execute("SELECT * FROM ai_reply.generation_attempt").fetchone()
    return rset, attempt


def generic_provider():
    return ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(3, legacy=True)],
                             "gate": [gate_output(["G1", "G2", "G3"], quality=4)], "depth_translate": translate})


@pytest.mark.pg
def test_disabled_keeps_existing_order_and_records(conn, database_url, sample_video, depth_server):
    model = FakeDepthModel({"밤새면": 0.9})
    provider = generic_provider()
    rset, attempt = run_generic(conn, database_url, sample_video, provider, depth_url=depth_server(model))
    assert [c["category"] for c in rset["cards"]] == ["question", "support", "empathy"]
    assert all("depth" not in c for c in rset["internal_cards"]) and "depth_ranking" not in attempt["candidates"]
    assert "depth_translate" not in [c.stage for c in provider.calls] and model.requests == []


@pytest.mark.pg
def test_enabled_orders_cards_by_depth_score_with_common_context(conn, database_url, sample_video, depth_server):
    model = FakeDepthModel({"밤새면": 0.9, "화이팅": 0.5, "무슨 과제": 0.1})
    provider = generic_provider()
    rset, attempt = run_generic(conn, database_url, sample_video, provider, depth_ranking_enabled=True,
                                depth_url=depth_server(model))
    assert [c["category"] for c in rset["cards"]] == ["empathy", "support", "question"]
    assert [c["card_id"] for c in rset["cards"]] == [c["card_id"] for c in rset["internal_cards"]]
    assert [c["example"] for c in rset["internal_cards"]] == ["밤새면 너무 피곤하죠", "마감 화이팅이에요!", "무슨 과제예요?"]
    assert [c["depth"]["rank"] for c in rset["internal_cards"]] == [1, 2, 3]
    assert rset["internal_cards"][0]["depth"] == {"score": 0.9, "rank": 1, "example_en": "EN 밤새면 너무 피곤하죠",
                                                  "model_id": "fake-depth#len50"}
    # 공통 맥락 C = 인용한 단체 대화 + 영상 게시글(캡션·장면). 생성 단계는 다시 부르지 않는다.
    sent = [c for c in provider.calls if c.stage == "depth_translate"]
    assert len(sent) == 1 and [c.stage for c in provider.calls].count("generic") == 1
    assert [t["turn_id"] for t in sent[0].payload["context_turns"]] == ["chat:1", "video"]
    assert sent[0].payload["context_turns"][1]["text"].startswith("과제 마감 D-1\n책상 위 노트북")
    context, replies = model.requests[0]
    assert context == ["EN C chat:1+video"]                           # 변환 단계가 압축한 C 한 턴만 점수 입력에 쓴다
    assert replies == ["EN 무슨 과제예요?", "EN 마감 화이팅이에요!", "EN 밤새면 너무 피곤하죠"]
    record_ = attempt["candidates"]["depth_ranking"]
    assert record_["status"] == "OK" and record_["model_id"] == "fake-depth#len50" and record_["context_en"] == context
    assert record_["ranked_order"] == [c["candidate_id"] for c in rset["internal_cards"]] == ["G3", "G2", "G1"]
    assert record_["candidate_order"] == ["G1", "G2", "G3"] and record_["selection_changed"] is False
    assert (record_["context_chat_turns"], record_["context_chat_turns_cited"]) == (1, 1)
    assert [i["truncated"] for i in record_["inputs"]] == [False, False, False]   # 실제 입력 토큰 수·잘림 기록
    assert record_["inputs"][0]["used_tokens"] == record_["inputs"][0]["input_tokens"] > 0
    spent = conn.execute("SELECT result FROM ai_reply.ai_job WHERE job_type = 'GENERIC'").fetchone()["result"]
    assert any(c["stage"].startswith("depth_translate:") for c in spent["calls"])  # 비용 예약·기록은 기존 방식


@pytest.mark.pg
def test_service_down_keeps_order_without_translation_cost(conn, database_url, sample_video):
    provider = generic_provider()
    rset, attempt = run_generic(conn, database_url, sample_video, provider, depth_ranking_enabled=True,
                                depth_url="http://127.0.0.1:9", depth_timeout_seconds=1)
    assert [c["category"] for c in rset["cards"]] == ["question", "support", "empathy"]
    assert attempt["candidates"]["depth_ranking"]["status"] == "SERVICE_UNAVAILABLE"
    assert "depth_translate" not in [c.stage for c in provider.calls]
    assert all("depth" not in c for c in rset["internal_cards"]) and rset["status"] == "READY"


@pytest.mark.pg
def test_translation_failure_keeps_order_and_job_succeeds(conn, database_url, sample_video, depth_server):
    model = FakeDepthModel({"밤새면": 0.9})
    provider = generic_provider()
    provider.responses["depth_translate"] = [errors.JobFailure(errors.REFUSAL, "depth_translate")]
    rset, attempt = run_generic(conn, database_url, sample_video, provider, depth_ranking_enabled=True,
                                depth_url=depth_server(model))
    assert [c["category"] for c in rset["cards"]] == ["question", "support", "empathy"]
    ranking = attempt["candidates"]["depth_ranking"]
    assert (ranking["status"], ranking["detail"]) == ("TRANSLATION_FAILED", "REFUSAL")
    assert conn.execute("SELECT status FROM ai_reply.ai_job WHERE job_type = 'GENERIC'").fetchone()["status"] == "SUCCEEDED"
    assert model.requests == []


@pytest.mark.pg
def test_personal_set_scored_with_same_common_context_not_personal_records(conn, database_url, sample_video,
                                                                          depth_server):
    model = FakeDepthModel({"밤샜어요": 0.95, "무슨 과제": 0.6, "화이팅": 0.2})
    records = [record("chat:9", "3", "저도 어제 과제 하느라 밤샜어요", role="own")]
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(2, legacy=True)],
                                 "gate": [gate_output(["G1", "G2"], quality=3)], "depth_translate": translate})
    start(conn, legacy=True)
    w = worker(database_url, provider, FakeFetcher(sample_video), depth_ranking_enabled=True, depth_url=depth_server(model))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    base = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    pinput = conn.execute(
        """INSERT INTO ai_reply.personal_input (viewer_id, video_id, analysis_id, base_set_id, personal_records,
               personal_snapshot_hash, version_id) VALUES (3, 101, %s, %s, %s, %s, %s) RETURNING id""",
        (base["analysis_id"], base["set_id"], jsonb(records), uuid.uuid4().hex, versions.active(conn)["version_id"])).fetchone()
    jobs.enqueue(conn, f"personal:{pinput['id']}", "PERSONAL", {"personal_input_id": pinput["id"]})
    base_context = model.requests[0][0]
    provider.responses["personal"] = [{"candidates": [personal_candidate("P1", "chat:9", "과제 하느라 밤샜어요")],
                                       "personalization_used": True, "reason": "개인 기록"}]
    provider.responses["gate"] = [gate_output(["P1"], quality=4, useful=True)]
    w.run_once(conn, "t")
    personal = conn.execute("SELECT * FROM ai_reply.recommendation_set WHERE kind = 'PERSONAL'").fetchone()
    assert [c["example"] for c in personal["internal_cards"]] == ["저도 어제 과제 하느라 밤샜어요", "무슨 과제예요?", "마감 화이팅이에요!"]
    assert [c["depth"]["rank"] for c in personal["internal_cards"]] == [1, 2, 3]
    translate_calls = [c for c in provider.calls if c.stage == "depth_translate"]
    personal_call = translate_calls[-1].payload
    assert personal_call["context_turns"] == []                       # 일반 세트의 영어 C 를 그대로 쓴다
    assert [c["text"] for c in personal_call["candidates"]] == ["저도 어제 과제 하느라 밤샜어요"]  # 새 카드만 변환
    assert "chat:9" not in str(translate_calls)                       # 개인 기록 P 는 점수 입력에 없다
    assert model.requests[-1][0] == base_context
    attempt = conn.execute("SELECT candidates FROM ai_reply.generation_attempt WHERE stage = 'PERSONAL'").fetchone()
    assert attempt["candidates"]["depth_ranking"]["context_reused_from"].startswith("generic-")
    assert {c["card_id"] for c in personal["cards"]} >= {c["card_id"] for c in base["cards"]}


# ───────────────────────── 재시도·긴 기록 ─────────────────────────

class _Job:
    id = 1


class FakeCtx:
    """select_generic·select_personal 이 쓰는 작업 문맥 대역(단계 캐시는 메모리)."""

    def __init__(self, settings, provider, stages=None):
        self.settings, self.provider, self.job = settings, provider, _Job()
        self.stages = dict(stages or {})

    def stage(self, key):
        return self.stages.get(key)

    def save_stage(self, key, value):
        self.stages[key] = value

    def model_call(self, key, request):
        cached = self.stages.get(key)
        if cached is not None:
            from reply_ai.provider import ModelResult
            return ModelResult.from_record(cached)
        result = self.provider.call(request)
        self.stages[key] = result.record()
        return result


SNAPSHOT = {"output_json": analysis_output(), "current_visual_description": "노트북과 커피잔"}


def _accepted(examples=("무슨 과제예요?", "마감 화이팅이에요!"), categories=("question", "support"), quality=4, prefix="G"):
    """accepted_candidates 결과 모양의 후보(유형·의미 그룹은 후보마다 다르다)."""
    return [{"candidate_id": f"{prefix}{n}", "category": c, "title": "t", "example": e, "quality": quality,
             "redundancy_group": f"grp-{prefix}{n}"} for n, (e, c) in enumerate(zip(examples, categories), 1)]


def test_retry_with_new_candidates_recomputes_instead_of_keyerror(depth_server):
    """HIGH-1: 점수 단계 저장 뒤 커밋 전에 끊겨 재시도하면 후보가 새로 만들어질 수 있다. 이전 캐시를 버리고 다시 계산한다."""
    model = FakeDepthModel({"화이팅": 0.8, "무슨 과제": 0.2, "커피": 0.5})
    settings = Settings(depth_ranking_enabled=True, depth_url=depth_server(model))
    provider = ScriptedProvider({"depth_translate": translate})
    source = {"caption": "과제 마감 D-1", "history_records": [record("chat:1", "11", "내일까지 과제 내야 해서 밤샐 듯")]}
    first = FakeCtx(settings, provider)
    selected, rec = depth.select_generic(first, _accepted(), source=source, snapshot=SNAPSHOT, task_id="generic-1-r1")
    assert rec["status"] == "OK" and [c["candidate_id"] for c in selected] == ["G2", "G1"]

    retry = FakeCtx(settings, provider, stages=first.stages)       # 같은 작업의 단계 캐시를 그대로 가진 재시도
    again = _accepted(("무슨 과제예요?", "마감 화이팅이에요!", "커피 몇 잔째예요?"), ("question", "support", "humor"))
    selected, rec = depth.select_generic(retry, again, source=source, snapshot=SNAPSHOT, task_id="generic-1-r1")
    assert rec["status"] == "OK" and [c["candidate_id"] for c in selected] == ["G2", "G3", "G1"]
    assert retry.stages["depth_score"]["card_ids"] == ["G1", "G2", "G3"]
    assert len(model.requests) == 2


def test_broken_cached_stage_fails_open_to_quality_selection():
    """캐시가 후보와 맞아도 내용이 깨졌으면 예외 없이 기존 품질순 선택(RANKING_INVALID)."""
    settings = Settings(depth_ranking_enabled=True, depth_url="http://127.0.0.1:9")
    stage = {"card_ids": ["G1", "G2"], "scores": {"G1": 0.1}, "candidates_en": {}, "model_id": "m"}
    ctx = FakeCtx(settings, ScriptedProvider({}), stages={"depth_score": stage})
    selected, rec = depth.select_generic(ctx, _accepted(), source={"caption": "c", "history_records": []},
                                         snapshot=SNAPSHOT, task_id="t")
    assert [c["candidate_id"] for c in selected] == ["G1", "G2"] and rec["status"] == "RANKING_INVALID"
    assert all("depth" not in c for c in selected)


def test_many_cited_turns_send_only_recent_n(depth_server):
    """MED-3: 인용 단체 대화가 40턴이어도 최근 N 턴만 변환에 보내고 선택이 된다."""
    model = FakeDepthModel({"화이팅": 0.8})
    history = [record(f"chat:{i}", "3", f"대화 {i}", minutes_before=100 - i) for i in range(1, 41)]
    output = {**analysis_output(), "context_links": [{**analysis_output()["context_links"][0],
                                                      "source_quotes": [{"source_id": r["source_id"], "quote": r["text"]}
                                                                        for r in history]}]}
    snapshot = {"output_json": output, "current_visual_description": "노트북"}
    provider = ScriptedProvider({"depth_translate": translate})
    ctx = FakeCtx(Settings(depth_ranking_enabled=True, depth_url=depth_server(model), depth_context_chat_turns=3), provider)
    _, rec = depth.select_generic(ctx, _accepted(), source={"caption": "c", "history_records": history},
                                  snapshot=snapshot, task_id="t")
    sent = provider.calls[0].payload["context_turns"]
    assert [t["turn_id"] for t in sent] == ["chat:38", "chat:39", "chat:40", "video"]
    assert rec["status"] == "OK" and (rec["context_chat_turns"], rec["context_chat_turns_cited"]) == (3, 40)
    assert depth.common_context_turns({"caption": "c", "history_records": history}, snapshot, 0)[-1]["turn_id"] == "video"


def test_fewer_cited_turns_than_limit_keeps_all():
    """인용 턴이 N 보다 적으면 전부 넣는다(3턴, N=4 → 3턴 + 영상)."""
    history = [record(f"chat:{i}", "3", f"대화 {i}", minutes_before=10 - i) for i in range(1, 4)]
    output = {**analysis_output(), "context_links": [{**analysis_output()["context_links"][0],
                                                      "source_quotes": [{"source_id": r["source_id"], "quote": r["text"]}
                                                                        for r in history]}]}
    snapshot = {"output_json": output, "current_visual_description": "노트북"}
    turns = depth.common_context_turns({"caption": "c", "history_records": history}, snapshot, 4)
    assert [t["turn_id"] for t in turns] == ["chat:1", "chat:2", "chat:3", "video"]


# ───────────────────────── 한 풀 선택(select_pool) ─────────────────────────

def _pool_item(cid, category, group, quality=4):
    return {"candidate_id": cid, "category": category, "redundancy_group": group, "quality": quality}


def test_select_pool_one_per_category_and_group_at_most_three_by_depth():
    pool = [_pool_item("G1", "question", "a"), _pool_item("G2", "support", "b"), _pool_item("G3", "empathy", "c"),
            _pool_item("P1", "question", "d"), _pool_item("P2", "humor", "b"), _pool_item("P3", "compliment", "e")]
    scores = {"G1": 0.5, "G2": 0.6, "G3": 0.2, "P1": 0.9, "P2": 0.8, "P3": 0.3}
    # P1(0.9) → P2(0.8, 그룹 b) → G2(0.6, 그룹 b 중복 제외) → G1(유형 question 중복 제외) → P3(0.3) 에서 3개
    assert [c["candidate_id"] for c in depth.select_pool(pool, scores)] == ["P1", "P2", "P3"]


def test_select_pool_ties_keep_pool_order_and_quality_is_not_used():
    pool = [_pool_item("G1", "question", "a", quality=4), _pool_item("G2", "support", "b", quality=3),
            _pool_item("G3", "empathy", "c", quality=4)]
    assert [c["candidate_id"] for c in depth.select_pool(pool, {"G1": 0.5, "G2": 0.5, "G3": 0.7})] == ["G3", "G1", "G2"]
    assert [c["candidate_id"] for c in depth.select_pool(pool, {"G1": 0.1, "G2": 0.9, "G3": 0.1})][0] == "G2"


def test_select_pool_without_scores_is_existing_quality_selection():
    candidates = [cand(f"G{i}", c) for i, c in enumerate(["question", "question", "support", "compliment", "empathy"], 1)]
    assessments = [gate(c["candidate_id"], 4 if i < 2 else 3, f"g{i}") for i, c in enumerate(candidates)]
    accepted = generation.accepted_candidates(candidates, assessments)
    assert depth.select_pool(accepted) == generation.select_generic(accepted)


# ───────────────────────── 개인 세트(한 풀) ─────────────────────────

def _personal_run(conn, database_url, sample_video, depth_server, *, enabled, scores, personal_candidates,
                  personal_quality=3, generic_quality=4, personal_gate_groups=None, depth_url=None):
    """일반 후보 3개(품질 generic_quality) → 일반 세트, 그 뒤 개인 후보(품질 personal_quality)로 개인 세트."""
    model = FakeDepthModel(scores)
    records = [record("chat:9", "3", "저도 어제 과제 하느라 밤샜어요", role="own")]
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(3, legacy=True)],
                                 "gate": [gate_output(["G1", "G2", "G3"], quality=generic_quality)],
                                 "depth_translate": translate})
    start(conn, legacy=True)
    url = depth_server(model)
    w = worker(database_url, provider, FakeFetcher(sample_video), depth_ranking_enabled=enabled, depth_url=url)
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    base = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    pinput = conn.execute(
        """INSERT INTO ai_reply.personal_input (viewer_id, video_id, analysis_id, base_set_id, personal_records,
               personal_snapshot_hash, version_id) VALUES (3, 101, %s, %s, %s, %s, %s) RETURNING id""",
        (base["analysis_id"], base["set_id"], jsonb(records), uuid.uuid4().hex, versions.active(conn)["version_id"])).fetchone()
    jobs.enqueue(conn, f"personal:{pinput['id']}", "PERSONAL", {"personal_input_id": pinput["id"]})
    provider.responses["personal"] = [{"candidates": personal_candidates, "personalization_used": bool(personal_candidates),
                                       "reason": "개인 기록"}]
    ids = [c["candidate_id"] for c in personal_candidates]
    provider.responses["gate"] = [gate_output(ids, quality=personal_quality, useful=True, groups=personal_gate_groups)]
    if depth_url is not None:  # 개인 작업만 점수 서비스가 다른 주소(예: 꺼짐)
        w = worker(database_url, provider, FakeFetcher(sample_video), depth_ranking_enabled=enabled, depth_url=depth_url,
                   depth_timeout_seconds=1)
    w.run_once(conn, "t")
    personal = conn.execute("SELECT * FROM ai_reply.recommendation_set WHERE kind = 'PERSONAL'").fetchone()
    attempt = conn.execute("SELECT * FROM ai_reply.generation_attempt WHERE stage = 'PERSONAL'").fetchone()
    return base, personal, attempt, provider, model


def _two_personal():
    p1 = personal_candidate("P1", "chat:9", "과제 하느라 밤샜어요")                                  # self_disclosure
    p2 = {**personal_candidate("P2", "chat:9", "과제 하느라 밤샜어요", category="question"),
          "title": "나도 밤샜다며 언제 끝났는지 물어볼까요?", "example": "저도 밤샜는데 몇 시에 끝났어요?"}
    return [p1, p2]


POOL_SCORES = {"무슨 과제": 0.77, "화이팅": 0.70, "밤새면": 0.60, "밤샜어요": 0.85, "몇 시에": 0.82}


@pytest.mark.pg
def test_personal_higher_depth_adopted_even_with_lower_quality(conn, database_url, sample_video, depth_server):
    """개인 0.85·0.82 > 일반 0.77: 개인 후보가 채택된다(품질 3 < 일반 4 라도 — 품질은 통과 기준으로만 쓴다)."""
    base, personal, attempt, provider, model = _personal_run(conn, database_url, sample_video, depth_server, enabled=True,
                                                             scores=POOL_SCORES, personal_candidates=_two_personal())
    internal = personal["internal_cards"]
    assert [c["candidate_id"] for c in internal] == ["P1", "P2", "G2"]       # G1(question) 은 P2(question) 와 유형 중복
    assert [c["depth"]["score"] for c in internal] == [0.85, 0.82, 0.70] and [c["depth"]["rank"] for c in internal] == [1, 2, 3]
    base_g2 = next(c for c in base["internal_cards"] if c["candidate_id"] == "G2")
    assert internal[2]["card_id"] == base_g2["card_id"]                       # 일반 세트에 있던 후보는 같은 카드 id
    ranking = attempt["candidates"]["depth_ranking"]
    assert ranking["status"] == "OK" and ranking["scope"] == "pool" and ranking["selection_policy"] == depth.SELECTION_POLICY
    assert ranking["pool_order"] == ["G1", "G2", "G3", "P1", "P2"]
    assert ranking["generic_scores_reused"] == ["G1", "G2", "G3"] and ranking["generic_scores_from"].startswith("generic-")
    assert ranking["personal_selected"] == ["P1", "P2"]
    assert ranking["baseline_selected"] == [c["candidate_id"] for c in base["internal_cards"]]  # 기존 병합이면 개인 0개
    # 개인 후보만 같은 공통 C 로 채점(일반 영어 C 재사용, 개인 기록 P 는 점수 입력에 없다)
    personal_translate = [c for c in provider.calls if c.stage == "depth_translate"][-1].payload
    assert personal_translate["context_turns"] == [] and len(personal_translate["candidates"]) == 2
    assert model.requests[-1][0] == model.requests[0][0] and len(model.requests[-1][1]) == 2
    assert "chat:9" not in str(model.requests)


@pytest.mark.pg
def test_personal_gate_input_includes_generic_accepted_candidates(conn, database_url, sample_video, depth_server):
    _, _, _, provider, _ = _personal_run(conn, database_url, sample_video, depth_server, enabled=True,
                                         scores=POOL_SCORES, personal_candidates=_two_personal())
    payload = [c for c in provider.calls if c.stage == "gate"][-1].payload
    assert [(c["candidate_id"], c["redundancy_group"]) for c in payload["generic_candidates"]] == \
        [("G1", "grp-G1"), ("G2", "grp-G2"), ("G3", "grp-G3")]
    assert payload["base_cards"] == []
    analysis_mod.validate_input("gate-input.schema.json", payload)


@pytest.mark.pg
def test_personal_same_group_as_generic_is_deduplicated_in_pool(conn, database_url, sample_video, depth_server):
    """개인 검수가 일반 후보와 같은 의미 그룹 이름을 쓰면 한 풀에서 그 그룹은 하나만 남는다."""
    _, personal, _, _, _ = _personal_run(conn, database_url, sample_video, depth_server, enabled=True,
                                         scores=POOL_SCORES, personal_candidates=_two_personal(),
                                         personal_gate_groups={"P1": "grp-G2"})
    assert [c["candidate_id"] for c in personal["internal_cards"]] == ["P1", "P2", "G3"]   # G2 는 P1 과 같은 그룹


@pytest.mark.pg
def test_no_personal_selected_keeps_generic_set(conn, database_url, sample_video, depth_server):
    """개인 후보 점수가 낮아 한 풀에서 하나도 뽑히지 않으면 일반 세트를 그대로 쓴다."""
    low = {**POOL_SCORES, "밤샜어요": 0.1, "몇 시에": 0.05}
    base, personal, attempt, _, _ = _personal_run(conn, database_url, sample_video, depth_server, enabled=True,
                                                  scores=low, personal_candidates=_two_personal(), personal_quality=4)
    assert personal is None and attempt["result_set_id"] == base["set_id"]
    assert attempt["candidates"]["depth_ranking"]["selected"] == [c["candidate_id"] for c in base["internal_cards"]]


@pytest.mark.pg
def test_personal_off_matches_existing_merge(conn, database_url, sample_video, depth_server):
    """Depth 꺼짐: 기존 병합(merge_personal) 결과·입력과 같다. 같은 품질이면 개인 후보가 교체하지 못한다."""
    base, personal, attempt, provider, model = _personal_run(conn, database_url, sample_video, depth_server, enabled=False,
                                                             scores=POOL_SCORES, personal_candidates=_two_personal(),
                                                             personal_quality=4, generic_quality=3)
    # 만석(3개·품질 3)에 품질 4 개인 후보: P1(self_disclosure)은 뒤 순위 카드(G3)를, P2(question)는 같은 유형 G1 을 교체
    assert [c["candidate_id"] for c in personal["internal_cards"]] == ["P2", "G2", "P1"]
    assert all("depth" not in c for c in personal["internal_cards"]) and "depth_ranking" not in attempt["candidates"]
    payload = [c for c in provider.calls if c.stage == "gate"][-1].payload
    assert "generic_candidates" not in payload and len(payload["base_cards"]) == 3
    assert model.requests == []


@pytest.mark.pg
def test_personal_depth_failure_falls_back_to_existing_merge(conn, database_url, sample_video, depth_server):
    """개인 작업의 점수 서비스 실패: 기존 병합 결과(꺼짐과 같은 카드)와 상태 기록."""
    base, personal, attempt, _, _ = _personal_run(conn, database_url, sample_video, depth_server, enabled=True,
                                                  scores=POOL_SCORES, personal_candidates=_two_personal(),
                                                  personal_quality=4, generic_quality=3, depth_url="http://127.0.0.1:9")
    assert {c["candidate_id"] for c in personal["internal_cards"]} == {"P1", "P2", "G2"}
    assert all("depth" not in c for c in personal["internal_cards"])
    assert attempt["candidates"]["depth_ranking"]["status"] == "SERVICE_UNAVAILABLE"


def test_fail_open_maps_generic_group_names_to_base_markers():
    """Depth 켜짐에서 실패: 개인 검수가 일반 그룹 이름을 썼으면 기존 병합의 base:<card_id> 판정으로 바꾼다."""
    base_internal = [{**_accepted()[0], "card_id": "c1"}]
    personal = [{**_accepted(("저도 밤샜어요",), ("self_disclosure",), prefix="P")[0], "redundancy_group": "grp-G1",
                 "personalization": {"kind": "past_experience"}, "current_anchor": "a", "reference_ids": []}]
    assert pipeline._merge_fail_open(base_internal, personal, generic_groups=True) == []    # 다른 유형·같은 의미 → 버림
    merged = pipeline._merge_fail_open(base_internal, personal, generic_groups=False)       # 이름이 달라 보이면 추가
    assert [c["candidate_id"] for c in merged] == ["G1", "P1"] and merged[1]["redundancy_group"] == "grp-G1"


# ───────────────────────── 선택 단계(일반 세트) ─────────────────────────

def _selection_provider(qualities=None):
    """후보 4개(G4 커피 질문은 G1 과 같은 의미 그룹). 기존 선택: G1·G2·G3(G4 는 G1 과 중복이라 빠진다)."""
    gate = gate_output(["G1", "G2", "G3", "G4"], quality=4, groups={"G4": "grp-G1"})
    for a in gate["assessments"]:
        a["quality"] = (qualities or {}).get(a["candidate_id"], 4)
    return ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(4, legacy=True)], "gate": [gate],
                             "depth_translate": translate})


SELECTION_SCORES = {"커피": 0.9, "밤새면": 0.6, "화이팅": 0.5, "무슨 과제": 0.1}


def _categories(rset):
    return [c["category"] for c in rset["cards"]]


@pytest.mark.pg
def test_selection_off_and_service_failure_match_existing_selection(conn, database_url, sample_video, depth_server):
    off, _ = run_generic(conn, database_url, sample_video, _selection_provider(),
                         depth_url=depth_server(FakeDepthModel(SELECTION_SCORES)))
    assert _categories(off) == ["question", "support", "empathy"]
    conn.execute("TRUNCATE ai_reply.ai_job, ai_reply.analysis_request, ai_reply.analysis_input, ai_reply.analysis_snapshot, "
                 "ai_reply.generation_attempt, ai_reply.recommendation_set, ai_reply.evidence_source, ai_reply.budget_day "
                 "RESTART IDENTITY CASCADE")
    provider = _selection_provider()
    failed, attempt = run_generic(conn, database_url, sample_video, provider, depth_ranking_enabled=True,
                                  depth_url="http://127.0.0.1:9", depth_timeout_seconds=1)
    assert _categories(failed) == _categories(off) and all("depth" not in c for c in failed["internal_cards"])
    ranking = attempt["candidates"]["depth_ranking"]
    assert ranking["status"] == "SERVICE_UNAVAILABLE" and ranking["baseline_selected"] == ["G1", "G2", "G3"]
    assert "depth_translate" not in [c.stage for c in provider.calls]


@pytest.mark.pg
def test_depth_changes_selection_within_diversity_rules(conn, database_url, sample_video, depth_server):
    """같은 품질·같은 의미 그룹(G1 vs G4)에서 Depth 가 높은 G4 가 뽑힌다. 그룹·유형 중복 금지와 최대 3개는 그대로."""
    model = FakeDepthModel(SELECTION_SCORES)
    provider = _selection_provider()
    rset, attempt = run_generic(conn, database_url, sample_video, provider, depth_ranking_enabled=True,
                                depth_url=depth_server(model))
    assert _categories(rset) == ["humor", "empathy", "support"]            # 결과 카드도 점수순
    internal = rset["internal_cards"]
    assert len({c["category"] for c in internal}) == len({c["redundancy_group"] for c in internal}) == len(internal) == 3
    ranking = attempt["candidates"]["depth_ranking"]
    assert ranking["status"] == "OK" and ranking["selection_changed"] is True
    assert (ranking["added_by_depth"], ranking["removed_by_depth"]) == (["G4"], ["G1"])
    assert [s["candidate_id"] for s in ranking["scores"]] == ["G1", "G2", "G3", "G4"]  # 검수 통과 후보 전부의 점수
    assert ranking["prioritized_order"] == ["G4", "G3", "G2", "G1"] and ranking["selected"] == ["G4", "G3", "G2"]
    sent = [c for c in provider.calls if c.stage == "depth_translate"]
    assert len(sent) == 1 and len(sent[0].payload["candidates"]) == 4    # 영어 변환 1번에 후보 전부
    assert len(model.requests) == 1 and len(model.requests[0][1]) == 4


@pytest.mark.pg
def test_quality_is_only_a_pass_threshold_not_an_ordering(conn, database_url, sample_video, depth_server):
    """품질은 통과 기준(≥3)으로만 쓴다. 품질 3 인 G4 가 Depth 가 높으면 품질 4 인 G1(같은 의미 그룹)을 대신한다."""
    provider = _selection_provider(qualities={"G4": 3})
    rset, attempt = run_generic(conn, database_url, sample_video, provider, depth_ranking_enabled=True,
                                depth_url=depth_server(FakeDepthModel(SELECTION_SCORES)))
    assert _categories(rset) == ["humor", "empathy", "support"]
    ranking = attempt["candidates"]["depth_ranking"]
    assert ranking["selection_changed"] is True and ranking["baseline_selected"] == ["G1", "G2", "G3"]

"""Depth 평가 모델 랭킹: 토큰 배치·점수 위치(서비스), 정렬·fail-open·꺼짐=기존 동작(작업기)."""
from __future__ import annotations

import threading
import uuid
from http.server import ThreadingHTTPServer

import httpx
import pytest

from reply_ai import depth, errors, jobs, versions
from reply_ai.db import jsonb
from reply_ai.config import Settings
from reply_ai.depth_service import EOS_ID, _valid_request, build_ids, make_handler, pad_batch
from reply_ai.provider import ScriptedProvider

from .conftest import record
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
    ids = build_ids(fake_encode, context, reply, max_seq_len=50)
    assert len(ids) == 50
    assert ids[-5:] == [3] * 5 and ids[-6] == EOS_ID          # 답장·구분자는 그대로
    assert ids[:28] == [1] * 28 and ids[28] == EOS_ID         # 전체 62개 중 맨 앞 맥락 12개가 잘렸다
    assert build_ids(fake_encode, context, reply, max_seq_len=0) == \
        [1] * 40 + [EOS_ID] + [2] * 15 + [EOS_ID] + [3] * 5   # 0 이면 자르지 않는다


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
    model.torch, model.device, model.max_seq_len = torch, "cpu", 50
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
    start(conn)
    w = worker(database_url, provider, FakeFetcher(sample_video), **settings)
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    rset = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    attempt = conn.execute("SELECT * FROM ai_reply.generation_attempt").fetchone()
    return rset, attempt


def generic_provider():
    return ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(3)],
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
    assert [(c["depth"]["rank"], c["depth"]["original_rank"]) for c in rset["internal_cards"]] == [(1, 3), (2, 2), (3, 1)]
    assert rset["internal_cards"][0]["depth"] == {"score": 0.9, "rank": 1, "original_rank": 3,
                                                  "example_en": "EN 밤새면 너무 피곤하죠", "model_id": "fake-depth#len50"}
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
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(2)],
                                 "gate": [gate_output(["G1", "G2"], quality=3)], "depth_translate": translate})
    start(conn)
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


# ───────────────────────── 재시도·긴 기록·병합 순서 ─────────────────────────

class _Job:
    id = 1


class FakeCtx:
    """rank_cards 가 쓰는 작업 문맥 대역(단계 캐시는 메모리)."""

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


def _cards(ids, examples=("무슨 과제예요?", "마감 화이팅이에요!")):
    return [{"card_id": i, "candidate_id": f"G{n}", "category": "question", "title": "t", "example": e}
            for n, (i, e) in enumerate(zip(ids, examples), 1)]


def test_retry_with_new_card_ids_recomputes_instead_of_keyerror(depth_server):
    """HIGH-1: 점수 단계 저장 뒤 커밋 전에 끊겨 재시도하면 card_id 가 새로 생긴다. 이전 캐시를 버리고 다시 계산한다."""
    model = FakeDepthModel({"화이팅": 0.8, "무슨 과제": 0.2})
    settings = Settings(depth_ranking_enabled=True, depth_url=depth_server(model))
    provider = ScriptedProvider({"depth_translate": translate})
    source = {"caption": "과제 마감 D-1", "history_records": [record("chat:1", "11", "내일까지 과제 내야 해서 밤샐 듯")]}
    first = FakeCtx(settings, provider)
    ranked, rec = depth.rank_cards(first, _cards(["old1", "old2"]), source=source, snapshot=SNAPSHOT, task_id="generic-1-r1")
    assert rec["status"] == "OK" and [c["card_id"] for c in ranked] == ["old2", "old1"]

    retry = FakeCtx(settings, provider, stages=first.stages)       # 같은 작업의 단계 캐시를 그대로 가진 재시도
    ranked, rec = depth.rank_cards(retry, _cards(["new1", "new2"]), source=source, snapshot=SNAPSHOT, task_id="generic-1-r1")
    assert rec["status"] == "OK" and [c["card_id"] for c in ranked] == ["new2", "new1"]
    assert retry.stages["depth_score"]["card_ids"] == ["new1", "new2"]
    assert len(provider.calls) == 1                                 # 예문이 같으면 변환은 캐시를 다시 쓴다(비용 없음)
    assert len(model.requests) == 2


def test_broken_cached_stage_fails_open_in_original_order():
    """캐시가 카드와 맞아도 내용이 깨졌으면 예외 없이 원래 순서(RANKING_INVALID)."""
    settings = Settings(depth_ranking_enabled=True, depth_url="http://127.0.0.1:9")
    cards = _cards(["a", "b"])
    stage = {"card_ids": ["a", "b"], "scores": {"a": 0.1}, "candidates_en": {}, "model_id": "m"}
    ctx = FakeCtx(settings, ScriptedProvider({}), stages={"depth_score": stage})
    ranked, rec = depth.rank_cards(ctx, cards, source={"caption": "c", "history_records": []}, snapshot=SNAPSHOT, task_id="t")
    assert [c["card_id"] for c in ranked] == ["a", "b"] and rec["status"] == "RANKING_INVALID"


def test_many_cited_turns_send_only_recent_n(depth_server):
    """MED-3: 인용 단체 대화가 40턴이어도 최근 N 턴만 변환에 보내고 랭킹이 된다."""
    model = FakeDepthModel({"화이팅": 0.8})
    history = [record(f"chat:{i}", "3", f"대화 {i}", minutes_before=100 - i) for i in range(1, 41)]
    output = {**analysis_output(), "context_links": [{**analysis_output()["context_links"][0],
                                                      "source_quotes": [{"source_id": r["source_id"], "quote": r["text"]}
                                                                        for r in history]}]}
    snapshot = {"output_json": output, "current_visual_description": "노트북"}
    provider = ScriptedProvider({"depth_translate": translate})
    ctx = FakeCtx(Settings(depth_ranking_enabled=True, depth_url=depth_server(model), depth_context_chat_turns=3), provider)
    ranked, rec = depth.rank_cards(ctx, _cards(["a", "b"]), source={"caption": "c", "history_records": history},
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


def test_original_order_restores_pre_depth_order():
    cards = [{"card_id": "c", "depth": {"original_rank": 3}}, {"card_id": "a", "depth": {"original_rank": 1}},
             {"card_id": "b", "depth": {"original_rank": 2}}]
    assert [c["card_id"] for c in depth.original_order(cards)] == ["a", "b", "c"]
    assert depth.original_order([{"card_id": "x"}, {"card_id": "y"}]) == [{"card_id": "x"}, {"card_id": "y"}]


def _personal_merge(conn, database_url, sample_video, depth_server, enabled):
    """일반 카드 3개(같은 품질, 만석)에 품질 높은 개인 카드 1개: 병합은 '최저 동률이면 뒤 순위 카드'를 교체한다."""
    model = FakeDepthModel({"밤새면": 0.9, "화이팅": 0.5, "무슨 과제": 0.1, "밤샜어요": 0.95})
    records = [record("chat:9", "3", "저도 어제 과제 하느라 밤샜어요", role="own")]
    provider = ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(3)],
                                 "gate": [gate_output(["G1", "G2", "G3"], quality=3)], "depth_translate": translate})
    start(conn)
    w = worker(database_url, provider, FakeFetcher(sample_video), depth_ranking_enabled=enabled, depth_url=depth_server(model))
    w.run_once(conn, "t")
    w.run_once(conn, "t")
    base = conn.execute("SELECT * FROM ai_reply.recommendation_set").fetchone()
    pinput = conn.execute(
        """INSERT INTO ai_reply.personal_input (viewer_id, video_id, analysis_id, base_set_id, personal_records,
               personal_snapshot_hash, version_id) VALUES (3, 101, %s, %s, %s, %s, %s) RETURNING id""",
        (base["analysis_id"], base["set_id"], jsonb(records), uuid.uuid4().hex, versions.active(conn)["version_id"])).fetchone()
    jobs.enqueue(conn, f"personal:{pinput['id']}", "PERSONAL", {"personal_input_id": pinput["id"]})
    provider.responses["personal"] = [{"candidates": [personal_candidate("P1", "chat:9", "과제 하느라 밤샜어요")],
                                       "personalization_used": True, "reason": "개인 기록"}]
    provider.responses["gate"] = [gate_output(["P1"], quality=4, useful=True)]
    w.run_once(conn, "t")
    personal = conn.execute("SELECT * FROM ai_reply.recommendation_set WHERE kind = 'PERSONAL'").fetchone()
    personal_call = next(c for c in provider.calls if c.stage == "personal")
    return base, personal, personal_call


@pytest.mark.pg
def test_depth_order_does_not_change_personal_merge(conn, database_url, sample_video, depth_server):
    """LOW-1: 일반 세트가 Depth 순(empathy 먼저)으로 저장돼도 병합·개인화 입력은 원래 순서 기준이다."""
    base, personal, call = _personal_merge(conn, database_url, sample_video, depth_server, enabled=True)
    assert [c["category"] for c in base["cards"]] == ["empathy", "support", "question"]
    assert [b["example"] for b in call.payload["base_cards"]] == ["무슨 과제예요?", "마감 화이팅이에요!", "밤새면 너무 피곤하죠"]
    kept = {c["category"] for c in personal["internal_cards"]}
    assert kept == {"question", "support", "self_disclosure"}        # 원래 순서의 마지막(empathy)이 교체된다
    assert [c["category"] for c in personal["cards"]] == ["self_disclosure", "support", "question"]  # 표시는 Depth 순


@pytest.mark.pg
def test_personal_merge_same_when_depth_disabled(conn, database_url, sample_video, depth_server):
    _, personal, _ = _personal_merge(conn, database_url, sample_video, depth_server, enabled=False)
    assert [c["category"] for c in personal["cards"]] == ["question", "support", "self_disclosure"]


# ───────────────────────── 선택 단계(일반 세트) ─────────────────────────

def _selection_provider(qualities=None):
    """후보 4개(G4 커피 질문은 G1 과 같은 의미 그룹). 기존 선택: G1·G2·G3(G4 는 G1 과 중복이라 빠진다)."""
    gate = gate_output(["G1", "G2", "G3", "G4"], quality=4, groups={"G4": "grp-G1"})
    for a in gate["assessments"]:
        a["quality"] = (qualities or {}).get(a["candidate_id"], 4)
    return ScriptedProvider({"analysis": [analysis_output()], "generic": [candidates_output(4)], "gate": [gate],
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
    assert [c["depth"]["original_rank"] for c in internal] == [3, 2, 1]  # Depth 없이 정했을 순서(병합용)


@pytest.mark.pg
def test_quality_stays_ahead_of_depth(conn, database_url, sample_video, depth_server):
    """Depth 는 같은 품질 안에서만 우선순위를 바꾼다. 품질 4 인 G1 이 품질 3 인 G4 보다 먼저다."""
    provider = _selection_provider(qualities={"G4": 3})
    rset, attempt = run_generic(conn, database_url, sample_video, provider, depth_ranking_enabled=True,
                                depth_url=depth_server(FakeDepthModel(SELECTION_SCORES)))
    assert {c["category"] for c in rset["cards"]} == {"question", "support", "empathy"}
    assert attempt["candidates"]["depth_ranking"]["selection_changed"] is False

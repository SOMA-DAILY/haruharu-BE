"""Cloudflare Workers AI 임베딩 연결(운영 배포 구현계획 4장·9장 순서 1): 합성 응답으로 정상·오류·한도·변화 감지를 확인한다.

실제 호출은 하지 않는다(httpx.MockTransport). 실제 연결 시험은 `reply-ai embedding probe` 로 따로 한다.
"""
import hashlib
import threading
import time

import httpx
import numpy as np
import pytest

from reply_ai import errors
from reply_ai.config import Settings
from reply_ai.embedding import (BATCH_MAX, CLOUDFLARE_MODEL, DIMENSIONS, PROVIDER_CLOUDFLARE, CloudflareEmbedder,
                                HttpEmbedder, make_embedder, make_probe_baseline, probe_texts, version_of)

TOKEN = "cf-test-token-should-never-leak"
ACCOUNT = "acct123"


def raw_vector(text: str) -> list[float]:
    """글마다 결정적인(정규화하지 않은) 1024차원 벡터 — 관리형 API 가 정규화해 준다고 가정하지 않는다."""
    seed = int.from_bytes(hashlib.sha256(text.encode("utf-8")).digest()[:8], "big")
    return (np.random.default_rng(seed).normal(size=DIMENSIONS) * 3.0).tolist()


def unit(text: str) -> np.ndarray:
    v = np.asarray(raw_vector(text), dtype=np.float64)
    return (v / np.linalg.norm(v)).astype(np.float32)


def baseline_for(texts=None) -> dict:
    texts = texts or probe_texts()
    return {"provider": PROVIDER_CLOUDFLARE, "model_id": CLOUDFLARE_MODEL, "request_mode": "text",
            "dimensions": DIMENSIONS, "baseline_id": "20261109", "texts": texts, "vectors": [unit(t).tolist() for t in texts]}


class Fake:
    """Cloudflare REST 흉내. 요청을 기록하고, 응답 모양을 바꿔 끼울 수 있다."""

    def __init__(self, respond=None, wrap=True, delay=0.0):
        self.requests: list[httpx.Request] = []
        self.respond = respond
        self.wrap = wrap
        self.delay = delay
        self.active = 0
        self.max_active = 0
        self._lock = threading.Lock()

    def __call__(self, request: httpx.Request) -> httpx.Response:
        with self._lock:
            self.requests.append(request)
            self.active += 1
            self.max_active = max(self.max_active, self.active)
        try:
            if self.delay:
                time.sleep(self.delay)
            texts = __import__("json").loads(request.content)["text"]
            if self.respond is not None:
                return self.respond(texts)
            result = {"shape": [len(texts), DIMENSIONS], "data": [raw_vector(t) for t in texts]}
            return httpx.Response(200, json={"result": result, "success": True, "errors": [], "messages": []}
                                  if self.wrap else result)
        finally:
            with self._lock:
                self.active -= 1

    def bodies(self):
        import json
        return [json.loads(r.content) for r in self.requests]


def embedder(fake: Fake, **kw) -> CloudflareEmbedder:
    kw.setdefault("baseline", baseline_for())
    return CloudflareEmbedder(ACCOUNT, TOKEN, client=httpx.Client(transport=httpx.MockTransport(fake)), **kw)


def test_sends_text_mode_with_bearer_token_and_returns_l2_normalized_vectors():
    fake = Fake()
    e = embedder(fake)
    out = e.embed(["문서 하나", "Instruct: x\nQuery:질의"])
    assert out.shape == (2, DIMENSIONS) and np.allclose(np.linalg.norm(out, axis=1), 1.0, atol=1e-4)
    assert np.allclose(out[0], unit("문서 하나"), atol=1e-5)  # 순서대로 대응
    last = fake.requests[-1]
    assert str(last.url) == f"https://api.cloudflare.com/client/v4/accounts/{ACCOUNT}/ai/run/{CLOUDFLARE_MODEL}"
    assert last.headers["authorization"] == "Bearer " + TOKEN
    # 질의 지시는 작업기가 문자열로 이미 붙였다 — 제공자의 queries/instruction 은 쓰지 않는다(지시 한 번)
    assert fake.bodies()[-1] == {"text": ["문서 하나", "Instruct: x\nQuery:질의"]}
    info = version_of(e.info())
    assert info == {"provider": "cloudflare_workers_ai", "model_id": "@cf/qwen/qwen3-embedding-0.6b",
                    "model_revision": "unpinned-20261109", "request_mode": "text", "dimensions": 1024, "normalization": "l2"}


def test_accepts_response_without_result_envelope():
    out = embedder(Fake(wrap=False)).embed(["a"])
    assert out.shape == (1, DIMENSIONS)


def _bad(kind):
    def respond(texts):
        n = len(texts)
        data = [raw_vector(t) for t in texts]
        if kind == "count":
            data = data[:-1] or []
        elif kind == "dims":
            data = [row[:512] for row in data]
        elif kind == "shape_field":
            return httpx.Response(200, json={"result": {"shape": [n, 768], "data": data}, "success": True})
        elif kind == "null":
            data[0][3] = None
        elif kind == "zero":
            data[0] = [0.0] * DIMENSIONS
        elif kind == "success_false":
            return httpx.Response(200, json={"result": None, "success": False, "errors": [{"code": 5006}]})
        return httpx.Response(200, json={"result": {"shape": [n, DIMENSIONS], "data": data}, "success": True})
    return respond


@pytest.mark.parametrize("kind", ["count", "dims", "shape_field", "null", "zero", "success_false"])
def test_malformed_vectors_fail_the_whole_batch_without_retry(kind):
    e = embedder(Fake(), baseline=baseline_for())
    e.ensure_stable()  # 점검은 정상 응답으로 먼저 통과시켜 둔다
    e._client = httpx.Client(transport=httpx.MockTransport(Fake(respond=_bad(kind))))
    with pytest.raises(errors.JobFailure) as info:
        e.embed(["가", "나"])
    assert info.value.code == errors.EMBEDDING_UNAVAILABLE and not info.value.retryable


def _status(code, headers=None):
    return lambda texts: httpx.Response(code, headers=headers or {}, json={"success": False, "errors": [{"code": 10000}]})


@pytest.mark.parametrize("respond, retryable, retry_after", [
    (_status(429, {"Retry-After": "7"}), True, 7.0),
    (_status(503), True, None),
    (_status(401), False, None),
    (_status(400), False, None),
])
def test_http_failures_are_classified(respond, retryable, retry_after):
    e = embedder(Fake())
    e.ensure_stable()
    e._client = httpx.Client(transport=httpx.MockTransport(Fake(respond=respond)))
    with pytest.raises(errors.JobFailure) as info:
        e.embed(["가"])
    assert info.value.code == errors.EMBEDDING_UNAVAILABLE
    assert info.value.retryable is retryable and info.value.retry_after == retry_after
    assert TOKEN not in str(info.value)


def test_timeout_is_retryable_and_token_never_leaks():
    def boom(texts):
        raise httpx.ReadTimeout("slow")
    e = embedder(Fake())
    e.ensure_stable()
    e._client = httpx.Client(transport=httpx.MockTransport(Fake(respond=boom)))
    with pytest.raises(errors.JobFailure) as info:
        e.embed(["가"])
    assert info.value.retryable and "timeout" in info.value.detail
    assert TOKEN not in repr(e) and TOKEN not in str(info.value) and TOKEN not in repr(e.info())


def test_input_over_4096_bytes_fails_as_input_too_long_without_calling():
    fake = Fake()
    e = embedder(fake)
    text = "가" * 1366  # 4,098바이트(한글 한 글자 3바이트) — 자르지 않는다
    assert not e.fits(text) and e.fits("가" * 1365)
    with pytest.raises(errors.JobFailure) as info:
        e.embed(["짧은 글", text])
    assert info.value.code == errors.INPUT_TOO_LONG and not fake.requests


def test_sends_at_most_32_per_request_and_2_requests_at_a_time():
    fake = Fake(delay=0.05)
    e = embedder(fake)
    e.ensure_stable()
    fake.requests.clear()
    texts = [f"글 {i}" for i in range(70)]
    out = e.embed(texts)
    assert [len(b["text"]) for b in fake.bodies()] == [BATCH_MAX, BATCH_MAX, 6]
    assert np.allclose(out[69], unit("글 69"), atol=1e-5)
    threads = [threading.Thread(target=e.embed, args=([f"동시 {i}"],)) for i in range(6)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    assert fake.max_active <= 2


def test_probe_check_runs_once_per_day_and_blocks_on_drift():
    fake = Fake()
    e = embedder(fake)
    e.embed(["가"])
    e.embed(["나"])
    probe_calls = [b for b in fake.bodies() if b["text"] == probe_texts()]
    assert len(probe_calls) == 1  # 하루에 한 번만 점검

    drifted = baseline_for()
    drifted["vectors"] = [(-np.asarray(v)).tolist() for v in drifted["vectors"]]  # 출력이 바뀐 것처럼
    with pytest.raises(errors.JobFailure) as info:
        embedder(Fake(), baseline=drifted).embed(["가"])
    assert info.value.code == errors.EMBEDDING_UNAVAILABLE and "drifted" in info.value.detail and not info.value.retryable

    with pytest.raises(errors.JobFailure) as missing:
        embedder(Fake(), baseline={}).embed(["가"])
    assert "no probe baseline" in missing.value.detail

    other = baseline_for() | {"request_mode": "queries"}
    with pytest.raises(errors.JobFailure) as wrong:
        embedder(Fake(), baseline=other).embed(["가"])
    assert "another embedding system" in wrong.value.detail


def test_make_probe_baseline_checks_repeat_and_order():
    baseline = make_probe_baseline(embedder(Fake(), baseline={}))
    assert baseline["texts"] == probe_texts() and len(baseline["vectors"]) == 4
    assert baseline["repeat_min_cosine"] > 0.9999 and baseline["order_min_cosine"] > 0.9999
    assert baseline["provider"] == PROVIDER_CLOUDFLARE and baseline["request_mode"] == "text"

    def scrambled(texts):  # 여러 개를 보내면 순서를 바꿔 돌려주는 잘못된 제공자
        rows = [raw_vector(t) for t in texts]
        rows = rows[1:] + rows[:1] if len(rows) > 1 else rows
        return httpx.Response(200, json={"result": {"shape": [len(texts), DIMENSIONS], "data": rows}, "success": True})
    with pytest.raises(errors.JobFailure):
        make_probe_baseline(embedder(Fake(respond=scrambled), baseline={}))


def test_make_embedder_selects_provider_and_requires_credentials():
    e = make_embedder(Settings(embedding_provider="cloudflare_workers_ai", cloudflare_account_id=ACCOUNT,
                               cloudflare_api_token=TOKEN))
    assert isinstance(e, CloudflareEmbedder) and TOKEN not in repr(e)
    assert isinstance(make_embedder(Settings()), HttpEmbedder)
    with pytest.raises(ValueError):
        make_embedder(Settings(embedding_provider="cloudflare_workers_ai", cloudflare_account_id=ACCOUNT))
    with pytest.raises(ValueError):
        make_embedder(Settings(embedding_provider="lm_studio"))
    assert TOKEN not in repr(Settings(cloudflare_api_token=TOKEN))

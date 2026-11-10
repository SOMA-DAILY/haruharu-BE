"""DialogRPT-depth 점수 서비스(별도 프로세스). revision 을 고정하지 않으면 시작하지 않는다.

- 모델: microsoft/DialogRPT-depth (GPT-2 medium 24층, GPT2ForSequenceClassification, num_labels=1), MIT.
- 입력: 맥락 턴마다 `<|endoftext|>`(50256) 로 끝내고 답장을 이어 붙인다. max_seq_len(학습 기본 50) 을 넘으면
  앞쪽(맥락 앞)부터 잘라낸다(공식 feeder 와 같다).
- 점수: 답장 마지막 토큰 위치(길이−1)의 logit → sigmoid.
  구분자와 패딩이 같은 50256 이라 분류 헤드의 자동 위치 선택(첫 pad 앞)은 맥락 끝을 고를 수 있다. 그래서 헤드에 맡기지 않고
  `model.transformer` 은닉상태 + `model.score` 로 직접 계산해 각 줄의 실제 길이−1 위치에서 꺼낸다.
- 시작할 때 공식 코드(golsun/DialogRPT `src/score.py`)가 낸 depth 점수(doc/toy.tsv.ensemble.jsonl)를 재현하는지 확인한다.
  모델 카드 표의 "Me too!" 0.032 는 같은 값이다. 카드의 "Can anyone recommend a nice review paper?" 0.724 는
  공식 계산 방식(마지막 답장 토큰)·다른 위치·다른 정밀도 어느 것으로도 나오지 않는다(이 구현 0.5986).
- 점수는 같은 맥락의 후보끼리 비교하는 값이다. 확률로 해석하지 않는다.
"""
from __future__ import annotations

import json
import logging
import math
import re
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Sequence

MODEL_ID = "microsoft/DialogRPT-depth"
PINNED_REVISION = "0fa8a8770e267801a5779788ccfd921192ae7f40"
EOS_ID = 50256
DEFAULT_MAX_SEQ_LEN = 50
MAX_CANDIDATES = 16
MAX_TURNS = 32
MAX_CHARS = 4000
# 공식 코드 출력(https://github.com/golsun/DialogRPT/blob/master/doc/toy.tsv.ensemble.jsonl 의 depth).
# 우리 계산이 소수 셋째 자리까지 같아야 한다(실제로는 여섯째 자리까지 같다).
REFERENCE_CHECKS = (
    ("I love NLP!", "Here’s a free textbook (URL) in case anyone needs it.", 0.4968094825744629),
    ("I love NLP!", "It’s super useful and more and more powerful!", 0.10862986743450165),
    ("I love NLP!", "Me too!", 0.031935129314661026),
    ("How are you?", "pretty good! :)", 0.111829474568367),
    ("How are you?", "Not bad.", 0.0771029144525528),
)
REFERENCE_TOLERANCE = 1e-3

log = logging.getLogger("reply_ai.depth")


def build_ids(encode, context_turns: Sequence[str], reply: str, max_seq_len: int) -> list[int]:
    """[turn₁ EOS turn₂ EOS … reply] 의 토큰 id. 길면 왼쪽(맥락 앞)부터 잘라 마지막 max_seq_len 개를 남긴다."""
    ids: list[int] = []
    for turn in context_turns:
        ids += list(encode(turn)) + [EOS_ID]
    ids += list(encode(reply))
    if max_seq_len > 0 and len(ids) > max_seq_len:
        ids = ids[-max_seq_len:]
    return ids


def pad_batch(rows: list[list[int]]) -> tuple[list[list[int]], list[list[int]], list[int]]:
    """오른쪽 패딩. (ids, attention_mask, 마지막 토큰 위치=길이−1)."""
    width = max(len(r) for r in rows)
    ids = [r + [EOS_ID] * (width - len(r)) for r in rows]
    mask = [[1] * len(r) + [0] * (width - len(r)) for r in rows]
    return ids, mask, [len(r) - 1 for r in rows]


def sigmoid(x: float) -> float:
    return 1.0 / (1.0 + math.exp(-x))


class DepthModel:
    def __init__(self, revision: str = PINNED_REVISION, device: str = "cpu", max_seq_len: int = DEFAULT_MAX_SEQ_LEN):
        if not re.fullmatch(r"[0-9a-f]{40}", revision or ""):
            raise SystemExit("depth revision must be pinned to a 40-hex commit")
        if max_seq_len < 8:
            raise SystemExit("depth max_seq_len must be ≥ 8")
        import torch
        import transformers
        from transformers import AutoTokenizer, GPT2ForSequenceClassification

        self.torch = torch
        self.tokenizer = AutoTokenizer.from_pretrained(MODEL_ID, revision=revision)
        self.model = GPT2ForSequenceClassification.from_pretrained(MODEL_ID, revision=revision, dtype=torch.float32)
        self.model.to(device).eval()
        if self.model.config.num_labels != 1:
            raise SystemExit(f"unexpected num_labels {self.model.config.num_labels}")
        self.device = device
        self.max_seq_len = max_seq_len
        versions = {"transformers": transformers.__version__, "torch": torch.__version__.split("+")[0]}
        system = f"{MODEL_ID}@{revision[:12]}#len{max_seq_len}#fp32#tf{versions['transformers']}#torch{versions['torch']}#{device}"
        self._lock = threading.Lock()
        self.info = {"model_id": system, "base_model": MODEL_ID, "model_revision": revision, "max_seq_len": max_seq_len,
                     "dtype": "float32", "device": device, "libraries": versions, "license": "MIT",
                     "score": "sigmoid(logit at last reply token)", "truncation": "left"}
        self.self_check()

    def _encode(self, text: str) -> list[int]:
        return self.tokenizer.encode(text, add_special_tokens=False)

    def inputs(self, context_turns: Sequence[str], replies: Sequence[str]) -> list[dict[str, Any]]:
        """답장마다 실제 입력 토큰 수(자르기 전·후)와 잘림 여부. 기록용."""
        context_tokens = sum(len(self._encode(t)) + 1 for t in context_turns)
        result = []
        for reply in replies:
            reply_tokens = len(self._encode(reply))
            full = context_tokens + reply_tokens
            used = min(full, self.max_seq_len) if self.max_seq_len > 0 else full
            result.append({"context_tokens": context_tokens, "reply_tokens": reply_tokens, "input_tokens": full,
                           "used_tokens": used, "truncated": used < full, "reply_truncated": reply_tokens > used})
        return result

    def score(self, context_turns: Sequence[str], replies: Sequence[str]) -> list[float]:
        torch = self.torch
        rows = [build_ids(self._encode, context_turns, reply, self.max_seq_len) for reply in replies]
        ids, mask, last = pad_batch(rows)
        with self._lock, torch.no_grad():
            input_ids = torch.tensor(ids, device=self.device)
            attention = torch.tensor(mask, device=self.device)
            hidden = self.model.transformer(input_ids=input_ids, attention_mask=attention).last_hidden_state
            logits = self.model.score(hidden)[:, :, 0]                      # [B, T]
            picked = logits[torch.arange(len(rows), device=self.device), torch.tensor(last, device=self.device)]
        return [sigmoid(float(v)) for v in picked.cpu().tolist()]

    def self_check(self) -> None:
        results = []
        for context in dict.fromkeys(c for c, _, _ in REFERENCE_CHECKS):
            expected = [(r, want) for c, r, want in REFERENCE_CHECKS if c == context]
            got = self.score([context], [r for r, _ in expected])   # 같은 맥락을 한 묶음(오른쪽 패딩)으로 계산
            for (reply, want), value in zip(expected, got, strict=True):
                if not math.isfinite(value) or abs(value - want) > REFERENCE_TOLERANCE:
                    raise SystemExit(f"depth self-check: {reply!r} → {value:.4f}, official {want:.4f}")
                results.append({"context": context, "reply": reply, "score": round(value, 4), "official": round(want, 4)})
        self.info["self_check"] = results


def _valid_request(body: Any) -> tuple[list[str], list[str]] | None:
    if not isinstance(body, dict):
        return None
    context, candidates = body.get("context_en"), body.get("candidates_en")
    if isinstance(context, str):
        context = [context]
    if isinstance(context, list) and len(context) > MAX_TURNS:
        context = context[-MAX_TURNS:]  # 앞쪽 턴은 어차피 왼쪽 자르기로 잘린다. 거절하지 않고 최근 턴만 쓴다.
    if not isinstance(context, list) \
            or not all(isinstance(t, str) and 0 < len(t.strip()) and len(t) <= MAX_CHARS for t in context):
        return None
    if not isinstance(candidates, list) or not 0 < len(candidates) <= MAX_CANDIDATES \
            or not all(isinstance(t, str) and 0 < len(t.strip()) and len(t) <= MAX_CHARS for t in candidates):
        return None
    return context, candidates


def make_handler(model: Any) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        def _send(self, status: int, body: dict[str, Any]) -> None:
            data = json.dumps(body).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self) -> None:  # noqa: N802
            if self.path == "/info":
                self._send(200, model.info)
            elif self.path == "/health":
                self._send(200, {"ok": True})
            else:
                self._send(404, {"error": "not_found"})

        def do_POST(self) -> None:  # noqa: N802
            if self.path != "/score":
                self._send(404, {"error": "not_found"})
                return
            try:
                length = int(self.headers.get("Content-Length") or 0)
                parsed = _valid_request(json.loads(self.rfile.read(length) or b"{}"))
                if parsed is None:
                    self._send(422, {"error": f"context_en: non-empty strings (last {MAX_TURNS} turns used), candidates_en: 1..{MAX_CANDIDATES} "
                                              f"non-empty strings ≤{MAX_CHARS} chars"})
                    return
                scores = model.score(*parsed)
                body = {"scores": scores, "model_id": model.info["model_id"],
                        "model_revision": model.info["model_revision"], "max_seq_len": model.info["max_seq_len"]}
                if hasattr(model, "inputs"):
                    body["inputs"] = model.inputs(*parsed)
                self._send(200, body)
            except json.JSONDecodeError:
                self._send(400, {"error": "invalid_json"})
            except Exception:  # 본문은 로그에 남기지 않는다.
                log.exception("depth_score_failed")
                self._send(500, {"error": "score_failed"})

        def log_message(self, fmt: str, *args: Any) -> None:
            log.info("depth_http %s", args[1] if len(args) > 1 else "")

    return Handler


def serve(host: str = "127.0.0.1", port: int = 18453, revision: str = PINNED_REVISION, device: str = "cpu",
          max_seq_len: int = DEFAULT_MAX_SEQ_LEN) -> None:
    model = DepthModel(revision=revision, device=device, max_seq_len=max_seq_len)
    server = ThreadingHTTPServer((host, port), make_handler(model))
    log.info("depth_ready")
    print(json.dumps({"event": "depth_ready", "host": host, "port": port,
                      **{k: model.info[k] for k in ("model_id", "model_revision", "max_seq_len", "self_check")}}), flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()

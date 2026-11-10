"""Qwen3-Embedding-0.6B 추론 서비스(별도 프로세스). revision 을 고정하지 않으면 시작하지 않는다.

- sentence-transformers 로 last-token pooling + L2 정규화, 1024 차원.
- query instruction 은 작업기가 한 번만 붙인다. 여기서는 어떤 prompt 도 자동으로 붙이지 않는다(default_prompt_name=None).
- 시작할 때 1024 개 유한값·norm≈1 을 확인한다(MODEL01).
- model_id 에 pooling·정규화·라이브러리 버전·장치를 넣어 다른 체계의 벡터와 절대 섞이지 않게 한다.
라이선스: Qwen3-Embedding-0.6B 는 Apache-2.0 (https://huggingface.co/Qwen/Qwen3-Embedding-0.6B). 가중치 학습 없이 추론만 한다.
"""
from __future__ import annotations

import json
import logging
import re
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

import numpy as np

MODEL_ID = "Qwen/Qwen3-Embedding-0.6B"
PINNED_REVISION = "97b0c614be4d77ee51c0cef4e5f07c00f9eb65b3"
DIMENSIONS = 1024
MAX_TEXTS = 64
MAX_CHARS = 8192

log = logging.getLogger("reply_ai.embedding")


class EmbeddingModel:
    def __init__(self, revision: str = PINNED_REVISION, device: str = "cpu"):
        if not re.fullmatch(r"[0-9a-f]{40}", revision or ""):
            raise SystemExit("embedding revision must be pinned to a 40-hex commit (PIN_BEFORE_BUILD is not allowed)")
        import sentence_transformers
        import torch
        import transformers
        from sentence_transformers import SentenceTransformer

        # dtype 를 float32 로 고정한다(체크포인트 기본 bfloat16 은 장치마다 값이 조금씩 달라질 수 있다).
        self.model = SentenceTransformer(MODEL_ID, revision=revision, device=device, model_kwargs={"dtype": torch.float32})
        self.model.default_prompt_name = None
        pooling = "unknown"
        for module in self.model:
            mode = getattr(module, "pooling_mode", None)
            if isinstance(mode, str):
                pooling = mode
            elif hasattr(module, "get_pooling_mode_str"):
                pooling = module.get_pooling_mode_str()
        if pooling != "lasttoken":
            raise SystemExit(f"unexpected pooling {pooling} (Qwen3-Embedding uses last-token pooling)")
        versions = {"sentence_transformers": sentence_transformers.__version__, "transformers": transformers.__version__,
                    "torch": torch.__version__}
        system = (f"{MODEL_ID}#{pooling}#norm#fp32#st{versions['sentence_transformers']}#tf{versions['transformers']}"
                  f"#torch{versions['torch'].split('+')[0]}#{device}")
        if len(system) > 100:
            raise SystemExit("embedding system id too long for ai_reply.embedding.model_id")
        self._lock = threading.Lock()
        self.info = {"model_id": system, "model_revision": revision, "dimensions": DIMENSIONS, "base_model": MODEL_ID,
                     "pooling": pooling, "normalize": True, "dtype": "float32", "device": device, "libraries": versions,
                     "license": "Apache-2.0", "query_prompt_applied_by_service": False}
        self.self_check()

    def embed(self, texts: list[str]) -> np.ndarray:
        with self._lock:
            vectors = self.model.encode(texts, normalize_embeddings=True, convert_to_numpy=True, batch_size=16,
                                        show_progress_bar=False)
        return np.asarray(vectors, dtype=np.float32)

    def self_check(self) -> None:
        vector = self.embed(["self-check"])
        if vector.shape != (1, DIMENSIONS):
            raise SystemExit(f"embedding self-check: shape {vector.shape}")
        if not np.all(np.isfinite(vector)):
            raise SystemExit("embedding self-check: non-finite values")
        norm = float(np.linalg.norm(vector[0]))
        if abs(norm - 1.0) > 1e-3:
            raise SystemExit(f"embedding self-check: norm {norm}")


def serve(host: str = "127.0.0.1", port: int = 18452, revision: str = PINNED_REVISION, device: str = "cpu") -> None:
    model = EmbeddingModel(revision=revision, device=device)

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
            if self.path != "/embed":
                self._send(404, {"error": "not_found"})
                return
            try:
                length = int(self.headers.get("Content-Length") or 0)
                body = json.loads(self.rfile.read(length) or b"{}")
                texts = body.get("texts")
                if not isinstance(texts, list) or not texts or len(texts) > MAX_TEXTS \
                        or not all(isinstance(t, str) and 0 < len(t) <= MAX_CHARS for t in texts):
                    self._send(422, {"error": "texts must be 1..64 non-empty strings ≤8192 chars"})
                    return
                vectors = model.embed(texts)
                self._send(200, {"vectors": vectors.tolist(), "model_id": model.info["model_id"],
                                 "model_revision": model.info["model_revision"], "dimensions": DIMENSIONS})
            except Exception:  # 본문은 로그에 남기지 않는다.
                log.exception("embed_failed")
                self._send(500, {"error": "embed_failed"})

        def log_message(self, fmt: str, *args: Any) -> None:  # 요청 본문·경로 이외 정보는 남기지 않는다
            log.info("embedding_http %s", args[1] if len(args) > 1 else "")

    server = ThreadingHTTPServer((host, port), Handler)
    log.info("embedding_ready")
    print(json.dumps({"event": "embedding_ready", "host": host, "port": port, **{k: model.info[k] for k in ("model_id", "model_revision", "dimensions")}}), flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()

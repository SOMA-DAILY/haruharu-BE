"""임베딩 클라이언트. 같은 제공자·모델·revision·요청 방식·차원·정규화(=embedding_version)의 벡터끼리만 비교한다.

- local_http: 로컬 embedding-serve(Qwen3-Embedding-0.6B, revision 고정). 개발·연구용.
- cloudflare_workers_ai: 운영. Cloudflare Workers AI 의 @cf/qwen/qwen3-embedding-0.6b 를 REST 로 직접 부른다
  (운영 배포 구현계획 4장). 별도 모델 서버·Workers 앱이 없어 Mac·LM Studio 가 꺼져 있어도 된다.
"""
from __future__ import annotations

import hashlib
import json
import math
import threading
import time
from datetime import datetime, timezone
from typing import Any, Protocol

import numpy as np

from . import errors

DIMENSIONS = 1024
BATCH_MAX = 32                      # Cloudflare 입력 배열 maxItems. 로컬 서비스에도 같은 묶음으로 보낸다
PROVIDER_LOCAL = "local_http"
PROVIDER_CLOUDFLARE = "cloudflare_workers_ai"
PROVIDER_TEST = "test_hash"
# 질의 지시는 retrieval.query_text 가 문자열로 한 번만 붙인다. 제공자의 queries/instruction 기능은 쓰지 않고
# 질의·문서 모두 text 로 보낸다(지시가 두 번 붙지 않게). 방식을 바꾸면 다른 임베딩 버전이다.
TEXT_MODE = "text"
NORMALIZATION = "l2"
CLOUDFLARE_MODEL = "@cf/qwen/qwen3-embedding-0.6b"
CLOUDFLARE_ENDPOINT = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/" + CLOUDFLARE_MODEL
PROBE_FILE = "embedding_probe.json"
DRIFT_MIN_COSINE = 0.999            # 고정 문장 출력이 이보다 멀어지면 관리형 모델이 바뀐 것으로 본다


class Embedder(Protocol):
    def info(self) -> dict[str, Any]: ...

    def embed(self, texts: list[str]) -> np.ndarray: ...


def version_of(info: dict[str, Any]) -> dict[str, Any]:
    """벡터를 섞어도 되는지 가르는 열쇠. 하나라도 다르면 다른 벡터 체계다."""
    return {"provider": info.get("provider", PROVIDER_LOCAL), "model_id": info["model_id"],
            "model_revision": info["model_revision"], "request_mode": info.get("request_mode", TEXT_MODE),
            "dimensions": int(info["dimensions"]), "normalization": info.get("normalization", NORMALIZATION)}


def fits(embedder: Any, text: str) -> bool:
    """입력 상한이 있는 임베더(Cloudflare)에서 이 글이 들어가는지. 상한이 없으면 항상 참."""
    check = getattr(embedder, "fits", None)
    return True if check is None else bool(check(text))


def check_vectors(vectors: np.ndarray, count: int, dimensions: int = DIMENSIONS) -> np.ndarray:
    """1024 차원·유한값·L2 정규화를 확인한다(MODEL01)."""
    if vectors.shape != (count, dimensions):
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, f"shape {vectors.shape}")
    if not np.all(np.isfinite(vectors)):
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "non-finite vector")
    norms = np.linalg.norm(vectors, axis=1)
    if np.any(np.abs(norms - 1.0) > 1e-3):
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "vector not L2-normalized")
    return vectors


def parse_vectors(result: Any, count: int, dimensions: int = DIMENSIONS) -> np.ndarray:
    """제공자 응답(data·shape)을 검사하고 L2 정규화한다. 하나라도 어긋나면 이 묶음 전체를 쓰지 않는다."""
    if not isinstance(result, dict) or not isinstance(result.get("data"), list):
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "response has no data")
    data = result["data"]
    if len(data) != count:
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, f"{len(data)} vectors for {count} texts")
    shape = result.get("shape")
    if shape is not None and list(shape) != [count, dimensions]:
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, f"shape field {shape}")
    if any(not isinstance(row, list) or len(row) != dimensions for row in data):
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "vector dimensions differ")
    try:
        array = np.asarray(data, dtype=np.float64)
    except (TypeError, ValueError):
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "non-numeric vector") from None
    if not np.all(np.isfinite(array)):
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "non-finite vector")
    norms = np.linalg.norm(array, axis=1)
    if np.any(norms <= 1e-12):
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "zero-norm vector")
    return check_vectors((array / norms[:, None]).astype(np.float32), count, dimensions)


class HttpEmbedder:
    """embedding-serve 서비스 클라이언트(로컬 개발)."""

    def __init__(self, url: str, timeout: float = 60.0):
        self.url = url.rstrip("/")
        self.timeout = timeout
        self._info: dict[str, Any] | None = None

    def info(self) -> dict[str, Any]:
        if self._info is None:
            self._info = self._request("GET", "/info")
        return self._info

    def embed(self, texts: list[str]) -> np.ndarray:
        if not texts:
            return np.zeros((0, DIMENSIONS), dtype=np.float32)
        out: list[np.ndarray] = []
        for start in range(0, len(texts), BATCH_MAX):
            chunk = texts[start:start + BATCH_MAX]
            body = self._request("POST", "/embed", {"texts": chunk})
            if version_of(body) != version_of(self.info()):
                raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "embedding service version changed")
            out.append(check_vectors(np.asarray(body["vectors"], dtype=np.float32), len(chunk), int(body["dimensions"])))
        return np.vstack(out)

    def _request(self, method: str, path: str, body: dict[str, Any] | None = None) -> dict[str, Any]:
        import httpx
        try:
            response = httpx.request(method, self.url + path, json=body, timeout=self.timeout)
        except httpx.HTTPError as error:
            raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, type(error).__name__, retryable=True) from None
        if response.status_code >= 500:
            raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, f"http {response.status_code}", retryable=True)
        if response.status_code != 200:
            raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, f"http {response.status_code}")
        return response.json()


def _retry_after(response: Any) -> float | None:
    value = response.headers.get("retry-after")
    try:
        return max(0.0, float(value)) if value else None
    except ValueError:
        return None


def _error_codes(response: Any) -> str:
    """Cloudflare 오류 본문에서 코드만 남긴다(메시지·요청 원문은 남기지 않는다)."""
    try:
        return ",".join(str(e.get("code")) for e in (response.json().get("errors") or [])[:3])
    except Exception:
        return ""


class CloudflareEmbedder:
    """Cloudflare Workers AI REST 의 Qwen3-Embedding-0.6B.

    - 토큰은 Workers AI 전용(읽기·편집)만 쓴다. repr·오류 메시지·로그에 넣지 않는다.
    - 시간 초과 30초, 동시 호출 2개(프로세스 전체), 한 번에 32개까지 보낸다.
    - 입력은 질의 지시 포함 UTF-8 4,096바이트까지. Qwen BPE 는 바이트 단위라 토큰 수 ≤ 바이트 수다(호스팅 한도 8,192 토큰).
      넘는 글은 자르지 않고 INPUT_TOO_LONG 으로 실패시킨다.
    - 관리형 모델은 불변 revision 을 주지 않는다. 그래서 revision 칸에는 'unpinned-<점검 기준 id>' 를 남기고,
      처음 쓰기 전(그 뒤 하루마다) 고정 문장 출력이 점검 기준과 같은지 본다. 달라졌으면 쓰지 않는다(조용히 섞지 않음).
    """

    def __init__(self, account_id: str, api_token: str, *, timeout: float = 30.0, concurrency: int = 2,
                 max_input_bytes: int = 4096, baseline: dict[str, Any] | None = None, client: Any = None,
                 drift_min_cosine: float = DRIFT_MIN_COSINE, recheck_seconds: float = 24 * 3600):
        if not account_id or not api_token:
            raise ValueError("CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN are required")
        self.url = CLOUDFLARE_ENDPOINT.format(account_id=account_id)
        self._headers = {"Authorization": "Bearer " + api_token}
        self.timeout = timeout
        self.max_input_bytes = max_input_bytes
        self.drift_min_cosine = drift_min_cosine
        self.recheck_seconds = recheck_seconds
        self._client = client
        self._slots = threading.BoundedSemaphore(max(1, concurrency))
        self._lock = threading.Lock()
        self._baseline = baseline if baseline is not None else load_probe_baseline()
        self._checked_at: float | None = None

    def __repr__(self) -> str:
        return f"CloudflareEmbedder(model={CLOUDFLARE_MODEL!r})"

    def info(self) -> dict[str, Any]:
        baseline_id = (self._baseline or {}).get("baseline_id", "none")
        return {"provider": PROVIDER_CLOUDFLARE, "model_id": CLOUDFLARE_MODEL, "model_revision": f"unpinned-{baseline_id}",
                "request_mode": TEXT_MODE, "dimensions": DIMENSIONS, "normalization": NORMALIZATION, "managed": True}

    def fits(self, text: str) -> bool:
        return len(text.encode("utf-8")) <= self.max_input_bytes

    def embed(self, texts: list[str]) -> np.ndarray:
        if not texts:
            return np.zeros((0, DIMENSIONS), dtype=np.float32)
        too_long = sum(1 for text in texts if not self.fits(text))
        if too_long:
            raise errors.JobFailure(errors.INPUT_TOO_LONG, f"{too_long} text(s) over {self.max_input_bytes} bytes")
        self.ensure_stable()
        return self.embed_unchecked(texts)

    def embed_unchecked(self, texts: list[str]) -> np.ndarray:
        """점검 기준 확인 없이 부른다(점검 자체와 기준 만들기에서만)."""
        return np.vstack([self._call(texts[start:start + BATCH_MAX]) for start in range(0, len(texts), BATCH_MAX)])

    def ensure_stable(self) -> None:
        with self._lock:
            if self._checked_at is not None and time.monotonic() - self._checked_at < self.recheck_seconds:
                return
            result = self.check_drift()
            if not result["stable"]:
                raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, result["reason"])
            self._checked_at = time.monotonic()

    def check_drift(self) -> dict[str, Any]:
        baseline = self._baseline
        if not baseline:
            return {"stable": False, "reason": "no probe baseline (run: reply-ai embedding probe --write)"}
        expected = {"provider": PROVIDER_CLOUDFLARE, "model_id": CLOUDFLARE_MODEL, "request_mode": TEXT_MODE,
                    "dimensions": DIMENSIONS}
        if any(baseline.get(key) != value for key, value in expected.items()):
            return {"stable": False, "reason": "probe baseline is for another embedding system"}
        vectors = self.embed_unchecked(list(baseline["texts"]))
        cosines = np.sum(vectors * np.asarray(baseline["vectors"], dtype=np.float32), axis=1)
        low = float(cosines.min())
        if low < self.drift_min_cosine:
            return {"stable": False, "reason": f"provider output drifted from baseline (min cosine {low:.5f})",
                    "min_cosine": low}
        return {"stable": True, "min_cosine": low, "baseline_id": baseline["baseline_id"]}

    def _http(self):
        if self._client is None:
            import httpx
            self._client = httpx.Client()
        return self._client

    def _call(self, chunk: list[str]) -> np.ndarray:
        import httpx
        with self._slots:
            try:
                response = self._http().post(self.url, json={"text": chunk}, headers=self._headers, timeout=self.timeout)
            except httpx.TimeoutException:
                raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "cloudflare timeout", retryable=True) from None
            except httpx.HTTPError as error:
                raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, f"cloudflare {type(error).__name__}",
                                        retryable=True) from None
        status = response.status_code
        if status == 429:
            raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "cloudflare rate limited", retryable=True,
                                    retry_after=_retry_after(response))
        if status >= 500:
            raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, f"cloudflare http {status}", retryable=True,
                                    retry_after=_retry_after(response))
        if status != 200:
            # 401·403(토큰·권한), 400(요청 형식·길이) 등은 다시 보내도 같다.
            raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, f"cloudflare http {status} {_error_codes(response)}".strip())
        try:
            body = response.json()
        except ValueError:
            raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, "cloudflare response is not json") from None
        if isinstance(body, dict) and body.get("success") is False:
            raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE, f"cloudflare success=false {_error_codes(response)}".strip())
        result = body.get("result", body) if isinstance(body, dict) else body
        return parse_vectors(result, len(chunk))


class HashEmbedder:
    """테스트·오프라인용 결정적 임베더(글자 2-gram 해시). 실제 모델과 절대 섞이지 않게 제공자·model_id 를 따로 쓴다."""

    def __init__(self, dimensions: int = DIMENSIONS):
        self.dimensions = dimensions

    def info(self) -> dict[str, Any]:
        return {"provider": PROVIDER_TEST, "model_id": "test/hash-bigram", "model_revision": "v1",
                "dimensions": self.dimensions, "pooling": "bag", "normalize": True}

    def embed(self, texts: list[str]) -> np.ndarray:
        rows = []
        for text in texts:
            vector = np.zeros(self.dimensions, dtype=np.float64)
            grams = [text[i:i + 2] for i in range(max(1, len(text) - 1))]
            for gram in grams:
                digest = hashlib.sha256(gram.encode("utf-8")).digest()
                vector[int.from_bytes(digest[:4], "big") % self.dimensions] += 1.0 if digest[4] % 2 else -1.0
            norm = math.sqrt(float(np.dot(vector, vector))) or 1.0
            rows.append(vector / norm)
        return np.asarray(rows, dtype=np.float32).reshape(len(texts), self.dimensions)


# ───────────────────────── 관리형 모델 점검 기준 ─────────────────────────

def probe_texts() -> list[str]:
    """고정 점검 문장(합성, 개인 정보 없음). 문서 2개 + 질의 2개(질의 지시 포함)로 실제 입력 모양과 같게 만든다."""
    from .resources import package_config
    from .retrieval import document_text, query_text
    instruction = package_config()["embedding"]["query_instruction"]
    return [
        document_text("오늘 한강 러닝 5km", "해 질 녘 강변 산책로를 달리는 사람의 뒷모습이 보인다."),
        document_text("과제 마감 D-1", "책상 위 노트북 화면에 발표 자료와 메모가 보인다."),
        query_text(instruction, "직접 만든 파스타", "접시에 담긴 토마토 파스타와 포크가 보인다."),
        query_text(instruction, "야구장 직관", "응원석에서 초록색 경기장을 내려다보는 장면이 보인다."),
    ]


def load_probe_baseline() -> dict[str, Any] | None:
    from .resources import ROOT
    path = ROOT / PROBE_FILE
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None


def make_probe_baseline(embedder: CloudflareEmbedder) -> dict[str, Any]:
    """연결 시험: 고정 문장을 두 번·순서를 바꿔 보내 결정성과 순서 대응을 확인하고 점검 기준을 만든다."""
    texts = probe_texts()
    first = embedder.embed_unchecked(texts)
    again = embedder.embed_unchecked(texts)
    reversed_rows = embedder.embed_unchecked(list(reversed(texts)))[::-1]
    repeat_min = float(np.min(np.sum(first * again, axis=1)))
    order_min = float(np.min(np.sum(first * reversed_rows, axis=1)))
    if repeat_min < DRIFT_MIN_COSINE or order_min < DRIFT_MIN_COSINE:
        raise errors.JobFailure(errors.EMBEDDING_UNAVAILABLE,
                                f"provider output not stable (repeat {repeat_min:.5f}, order {order_min:.5f})")
    now = datetime.now(timezone.utc)
    return {"provider": PROVIDER_CLOUDFLARE, "model_id": CLOUDFLARE_MODEL, "request_mode": TEXT_MODE,
            "dimensions": DIMENSIONS, "normalization": NORMALIZATION, "baseline_id": now.strftime("%Y%m%d"),
            "created_at": now.isoformat(timespec="seconds"), "repeat_min_cosine": round(repeat_min, 6),
            "order_min_cosine": round(order_min, 6), "texts": texts,
            "vectors": [[round(float(x), 7) for x in row] for row in first]}


def make_embedder(settings: Any) -> Embedder:
    """설정의 제공자로 임베더를 만든다. 알 수 없는 제공자는 시작하지 않는다."""
    if settings.embedding_provider == PROVIDER_CLOUDFLARE:
        return CloudflareEmbedder(settings.cloudflare_account_id, settings.cloudflare_api_token,
                                  timeout=settings.embedding_timeout_seconds, concurrency=settings.embedding_concurrency,
                                  max_input_bytes=settings.embedding_max_input_bytes)
    if settings.embedding_provider == PROVIDER_LOCAL:
        return HttpEmbedder(settings.embedding_url)
    raise ValueError(f"unknown REPLY_AI_EMBEDDING_PROVIDER {settings.embedding_provider!r}")

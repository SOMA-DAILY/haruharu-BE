"""작업 실패 분류. 재시도는 전송 계층 실패(네트워크·429·5xx·시간 초과)만 한다."""
from __future__ import annotations

# docs/engineering/reply-recommendation.md 의 failure_code 와 같다(+작업기 전용 몇 개).
TRANSPORT_EXHAUSTED = "TRANSPORT_EXHAUSTED"
REFUSAL = "REFUSAL"
INCOMPLETE = "INCOMPLETE"
SCHEMA_INVALID = "SCHEMA_INVALID"
SEMANTIC_INVALID = "SEMANTIC_INVALID"
BUDGET_EXCEEDED = "BUDGET_EXCEEDED"
MEDIA_SOURCE_NOT_ALLOWED = "MEDIA_SOURCE_NOT_ALLOWED"
MEDIA_DOWNLOAD_FAILED = "MEDIA_DOWNLOAD_FAILED"
MEDIA_DECODE_FAILED = "MEDIA_DECODE_FAILED"
MEDIA_BLACK_FRAMES = "MEDIA_BLACK_FRAMES"
INPUT_MISSING = "INPUT_MISSING"
INPUT_REVOKED = "INPUT_REVOKED"
# 작업기 전용: 모델 API 가 요청 자체를 거부(400·401·403·404·422)하거나 응답이 실패 상태.
PROVIDER_REJECTED = "PROVIDER_REJECTED"
PROVIDER_FAILED = "PROVIDER_FAILED"
# lease 만료로 다시 잡힌 작업이 max_attempts 를 넘김(작업기 중단이 반복됨).
ATTEMPTS_EXHAUSTED = "ATTEMPTS_EXHAUSTED"
EMBEDDING_UNAVAILABLE = "EMBEDDING_UNAVAILABLE"
# 임베딩 입력(질의 지시 포함)이 상한을 넘음. 몰래 자르지 않고 실패로 남긴다(운영 배포 계획 4장).
INPUT_TOO_LONG = "INPUT_TOO_LONG"
INTERNAL_ERROR = "INTERNAL_ERROR"


class JobFailure(Exception):
    """작업을 끝내는 실패. retryable=True 면 RETRY_WAIT(시도 횟수 안에서), 아니면 즉시 FAILED."""

    def __init__(self, code: str, detail: str = "", *, retryable: bool = False, retry_after: float | None = None):
        self.code = code
        self.detail = detail[:300]
        self.retryable = retryable
        self.retry_after = retry_after
        self.cost_usd = 0.0               # 응답을 받은 뒤의 실패면 실제 토큰 비용(정산용)
        self.provider_request_id: str | None = None
        super().__init__(f"{code}: {self.detail}" if self.detail else code)


class LeaseLost(Exception):
    """다른 작업기가 lease 를 가져갔다. 결과를 쓰지 않고 손을 뗀다."""


def transport(detail: str, retry_after: float | None = None) -> JobFailure:
    return JobFailure(TRANSPORT_EXHAUSTED, detail, retryable=True, retry_after=retry_after)


def semantic(detail: str) -> JobFailure:
    return JobFailure(SEMANTIC_INVALID, detail)

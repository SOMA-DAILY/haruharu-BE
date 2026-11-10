"""GPT 호출(Responses API). 단계마다 독립 호출하며 store=false·도구 없음·previous_response_id 없음.

출력은 json_schema strict 로 받고, API 가 받지 않는 스키마 키워드는 호출용 사본에서만 뺀다.
돌려받은 객체는 언제나 전체 계약 스키마로 다시 검사한다. 거절·잘린 응답은 정상 결과가 아니다.
"""
from __future__ import annotations

import base64
import copy
import email.utils
import json
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Protocol

import jsonschema

from . import errors
from .config import Settings
from .resources import stage_schema, strict_support
from .util import canonical


@dataclass(frozen=True)
class Image:
    jpeg: bytes
    detail: str = "high"

    def part(self) -> dict[str, Any]:
        encoded = base64.b64encode(self.jpeg).decode("ascii")
        return {"type": "input_image", "image_url": f"data:image/jpeg;base64,{encoded}", "detail": self.detail}


@dataclass(frozen=True)
class ModelRequest:
    stage: str
    instructions: str
    payload: dict[str, Any]
    images: tuple[Image, ...] = ()
    max_output_tokens: int = 4096


@dataclass
class ModelResult:
    output: dict[str, Any]
    usage: dict[str, int]
    provider_request_id: str | None
    model: str
    cost_usd: float
    meta: dict[str, Any] = field(default_factory=dict)

    def record(self) -> dict[str, Any]:
        """작업 단계 캐시·감사용(원문 요청은 넣지 않는다)."""
        return {"output": self.output, "usage": self.usage, "provider_request_id": self.provider_request_id,
                "model": self.model, "cost_usd": round(self.cost_usd, 6)}

    @classmethod
    def from_record(cls, value: dict[str, Any]) -> "ModelResult":
        return cls(output=value["output"], usage=value.get("usage", {}), provider_request_id=value.get("provider_request_id"),
                   model=value.get("model", ""), cost_usd=float(value.get("cost_usd", 0)), meta={"cached": True})


class Provider(Protocol):
    model: str

    def call(self, request: ModelRequest) -> ModelResult: ...


def api_schema(contract: dict[str, Any], unsupported: tuple[str, ...] | list[str] | None = None) -> dict[str, Any]:
    """strict 모드 호출용 사본. $schema 와 지원하지 않는 키워드만 뺀다(서버 검사는 원본으로 한다)."""
    drop = set(strict_support()["unsupported_keywords"] if unsupported is None else unsupported) | {"$schema"}

    def walk(node: Any, names: bool = False) -> Any:
        # names=True: properties 맵 안이라 키가 키워드가 아니라 칸 이름이다(지우지 않는다).
        if isinstance(node, dict):
            if names:
                return {k: walk(v) for k, v in node.items()}
            return {k: walk(v, names=(k == "properties")) for k, v in node.items() if k not in drop}
        if isinstance(node, list):
            return [walk(v) for v in node]
        return node

    return walk(copy.deepcopy(contract))


def validate_contract(stage: str, output: Any) -> None:
    try:
        jsonschema.Draft202012Validator(stage_schema(stage)).validate(output)
    except jsonschema.ValidationError as error:
        path = "/".join(str(p) for p in error.absolute_path)
        raise errors.JobFailure(errors.SCHEMA_INVALID, f"{stage}: {error.validator} at /{path}") from None


def cost_usd(settings: Settings, usage: dict[str, int]) -> float:
    return (usage.get("input_tokens", 0) * settings.price_input_per_m
            + usage.get("output_tokens", 0) * settings.price_output_per_m) / 1_000_000


def _retry_after(response: Any) -> float | None:
    headers = getattr(response, "headers", None) or {}
    try:
        if headers.get("retry-after-ms"):
            return float(headers["retry-after-ms"]) / 1000
        value = headers.get("retry-after")
        if value:
            try:
                return float(value)
            except ValueError:
                parsed = email.utils.parsedate_to_datetime(value)
                return max(0.0, parsed.timestamp() - time.time())
    except (TypeError, ValueError):
        return None
    return None


def _quota_exceeded(error: Any) -> bool:
    """429 중 계정 크레딧·한도 소진(insufficient_quota)인가. 속도 제한 429 와 달리 기다려도 풀리지 않는다."""
    code = getattr(error, "code", None)
    if code is None and isinstance(getattr(error, "body", None), dict):
        body = error.body
        code = (body.get("error") or {}).get("code") if isinstance(body.get("error"), dict) else body.get("code")
    return code == "insufficient_quota"


class OpenAIProvider:
    def __init__(self, settings: Settings, client: Any | None = None):
        if client is None:
            from openai import OpenAI
            if not settings.openai_api_key:
                raise RuntimeError("OPENAI_API_KEY is not set")
            # 재시도는 작업 단위 정책(전송 실패만, 최대 3회)이 맡는다. SDK 자체 재시도는 끈다.
            client = OpenAI(api_key=settings.openai_api_key, timeout=settings.llm_timeout_seconds, max_retries=0)
        self.client = client
        self.settings = settings
        self.model = settings.model

    def call(self, request: ModelRequest) -> ModelResult:
        import openai

        content: list[dict[str, Any]] = [{"type": "input_text", "text": canonical(request.payload)}]
        content.extend(image.part() for image in request.images)
        try:
            response = self.client.responses.create(
                model=self.model,
                instructions=request.instructions,
                input=[{"role": "user", "content": content}],
                reasoning={"effort": self.settings.reasoning_effort},
                store=False,
                max_output_tokens=request.max_output_tokens,
                text={"format": {"type": "json_schema", "name": f"{request.stage}_output",
                                 "schema": api_schema(stage_schema(request.stage)), "strict": True}},
            )
        except (openai.APITimeoutError, openai.APIConnectionError) as error:
            raise errors.transport(f"{request.stage}: {type(error).__name__}") from None
        except openai.RateLimitError as error:
            if _quota_exceeded(error):
                raise errors.JobFailure(errors.PROVIDER_QUOTA_EXCEEDED, f"{request.stage}: 429 insufficient_quota") from None
            raise errors.transport(f"{request.stage}: 429", _retry_after(error.response)) from None
        except openai.APIStatusError as error:
            status = getattr(error, "status_code", 0) or 0
            if status >= 500 or status in (408, 409):
                raise errors.transport(f"{request.stage}: {status}", _retry_after(error.response)) from None
            raise errors.JobFailure(errors.PROVIDER_REJECTED, f"{request.stage}: HTTP {status}") from None
        return self._result(request, response)

    def _result(self, request: ModelRequest, response: Any) -> ModelResult:
        usage = _usage(getattr(response, "usage", None))
        try:
            return self._parse(request, response, usage)
        except errors.JobFailure as failure:
            # 응답을 받았으면 거절·잘림·형식 위반이어도 토큰 비용은 쓴 것이다. 예약 정산에 쓴다.
            failure.cost_usd = cost_usd(self.settings, usage)
            failure.provider_request_id = getattr(response, "id", None)
            raise

    def _parse(self, request: ModelRequest, response: Any, usage: dict[str, int]) -> ModelResult:
        status = getattr(response, "status", None)
        if status == "incomplete":
            reason = getattr(getattr(response, "incomplete_details", None), "reason", None) or "unknown"
            raise errors.JobFailure(errors.INCOMPLETE, f"{request.stage}: {reason}")
        if status == "failed":
            code = getattr(getattr(response, "error", None), "code", None) or "unknown"
            if code in ("server_error", "rate_limit_exceeded"):
                raise errors.transport(f"{request.stage}: failed {code}")
            raise errors.JobFailure(errors.PROVIDER_FAILED, f"{request.stage}: {code}")
        if status != "completed":
            raise errors.JobFailure(errors.INCOMPLETE, f"{request.stage}: status {status}")
        texts, refused = [], False
        for item in getattr(response, "output", None) or []:
            if getattr(item, "type", None) != "message":
                continue
            for part in getattr(item, "content", None) or []:
                kind = getattr(part, "type", None)
                if kind == "refusal":
                    refused = True
                elif kind == "output_text":
                    texts.append(getattr(part, "text", ""))
        if refused:
            raise errors.JobFailure(errors.REFUSAL, request.stage)
        if len(texts) != 1:
            raise errors.JobFailure(errors.SCHEMA_INVALID, f"{request.stage}: {len(texts)} output texts")
        try:
            output = json.loads(texts[0])
        except json.JSONDecodeError:
            raise errors.JobFailure(errors.SCHEMA_INVALID, f"{request.stage}: not JSON") from None
        validate_contract(request.stage, output)
        return ModelResult(output=output, usage=usage, provider_request_id=getattr(response, "id", None),
                           model=getattr(response, "model", None) or self.model, cost_usd=cost_usd(self.settings, usage))


def _usage(value: Any) -> dict[str, int]:
    if value is None:
        return {}
    result = {"input_tokens": int(getattr(value, "input_tokens", 0) or 0),
              "output_tokens": int(getattr(value, "output_tokens", 0) or 0)}
    details = getattr(value, "output_tokens_details", None)
    if details is not None and getattr(details, "reasoning_tokens", None) is not None:
        result["reasoning_tokens"] = int(details.reasoning_tokens)
    cached = getattr(getattr(value, "input_tokens_details", None), "cached_tokens", None)
    if cached is not None:
        result["cached_tokens"] = int(cached)
    return result


class ScriptedProvider:
    """테스트용. 단계별 응답(객체·JobFailure·함수)을 차례로 돌려주고 받은 요청을 기록한다."""

    def __init__(self, responses: dict[str, list[Any] | Callable[[ModelRequest], Any]], model: str = "fake-model",
                 settings: Settings | None = None):
        self.responses = {k: (list(v) if isinstance(v, list) else v) for k, v in responses.items()}
        self.model = model
        self.calls: list[ModelRequest] = []
        self.settings = settings or Settings()

    def call(self, request: ModelRequest) -> ModelResult:
        self.calls.append(request)
        source = self.responses.get(request.stage)
        if source is None:
            raise AssertionError(f"unexpected model call: {request.stage}")
        value = source(request) if callable(source) else source.pop(0)
        if isinstance(value, Exception):
            raise value
        validate_contract(request.stage, value)
        usage = {"input_tokens": 1000, "output_tokens": 200}
        return ModelResult(output=value, usage=usage, provider_request_id=f"fake-{len(self.calls)}", model=self.model,
                           cost_usd=cost_usd(self.settings, usage))

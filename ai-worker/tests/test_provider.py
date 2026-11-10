"""Responses 호출 결과 분류: 거절·잘림·형식 위반은 정상 결과가 아니고, 429·5xx·네트워크만 재시도."""
import json
from types import SimpleNamespace

import httpx
import openai
import pytest

from reply_ai import errors
from reply_ai.config import Settings
from reply_ai.provider import ModelRequest, OpenAIProvider, api_schema
from reply_ai.resources import stage_schema

GOOD = {"verdict": "appropriate", "confidence": "high", "stable_meaning": True, "reason": "자연스럽다"}


class FakeResponses:
    def __init__(self, result):
        self.result = result
        self.kwargs = None

    def create(self, **kwargs):
        self.kwargs = kwargs
        if isinstance(self.result, Exception):
            raise self.result
        return self.result


def provider(result):
    responses = FakeResponses(result)
    return OpenAIProvider(Settings(openai_api_key="test"), client=SimpleNamespace(responses=responses)), responses


def response(status="completed", parts=None, incomplete=None):
    parts = parts if parts is not None else [SimpleNamespace(type="output_text", text=json.dumps(GOOD))]
    return SimpleNamespace(id="resp_1", model="gpt-6.1-terra", status=status, incomplete_details=SimpleNamespace(reason=incomplete),
                           error=None, output=[SimpleNamespace(type="reasoning"), SimpleNamespace(type="message", content=parts)],
                           usage=SimpleNamespace(input_tokens=1000, output_tokens=100, output_tokens_details=SimpleNamespace(reasoning_tokens=40),
                                                 input_tokens_details=SimpleNamespace(cached_tokens=0)))


REQUEST = ModelRequest("actual_appropriateness", "지시", {"context": {}}, max_output_tokens=100)


def test_success_uses_store_false_low_effort_and_strict_schema():
    p, fake = provider(response())
    result = p.call(REQUEST)
    assert result.output == GOOD and result.provider_request_id == "resp_1"
    assert abs(result.cost_usd - (1000 * 1 + 100 * 5) / 1_000_000) < 1e-9
    kw = fake.kwargs
    assert kw["store"] is False and kw["reasoning"] == {"effort": "low"} and "tools" not in kw and "previous_response_id" not in kw
    assert kw["text"]["format"]["strict"] is True and "$schema" not in kw["text"]["format"]["schema"]


def test_refusal_incomplete_and_bad_json_are_failures_not_results():
    for result, code in ((response(parts=[SimpleNamespace(type="refusal", refusal="no")]), errors.REFUSAL),
                         (response(status="incomplete", incomplete="max_output_tokens"), errors.INCOMPLETE),
                         (response(parts=[SimpleNamespace(type="output_text", text="{bad")]), errors.SCHEMA_INVALID),
                         (response(parts=[SimpleNamespace(type="output_text", text=json.dumps({**GOOD, "verdict": "maybe"}))]),
                          errors.SCHEMA_INVALID)):
        p, _ = provider(result)
        with pytest.raises(errors.JobFailure) as info:
            p.call(REQUEST)
        assert info.value.code == code and not info.value.retryable and info.value.cost_usd > 0


def status_error(cls, code, headers=None, body=None):
    request = httpx.Request("POST", "https://api.openai.com/v1/responses")
    return cls("x", response=httpx.Response(code, request=request, headers=headers or {}), body=body)


def test_only_transport_errors_are_retryable():
    p, _ = provider(status_error(openai.RateLimitError, 429, {"retry-after": "7"}))
    with pytest.raises(errors.JobFailure) as info:
        p.call(REQUEST)
    assert info.value.retryable and info.value.retry_after == 7.0
    p, _ = provider(status_error(openai.InternalServerError, 503))
    with pytest.raises(errors.JobFailure) as info:
        p.call(REQUEST)
    assert info.value.retryable
    p, _ = provider(openai.APIConnectionError(request=httpx.Request("POST", "https://api.openai.com")))
    with pytest.raises(errors.JobFailure) as info:
        p.call(REQUEST)
    assert info.value.retryable
    p, _ = provider(status_error(openai.BadRequestError, 400))
    with pytest.raises(errors.JobFailure) as info:
        p.call(REQUEST)
    assert not info.value.retryable and info.value.code == errors.PROVIDER_REJECTED


def test_insufficient_quota_429_fails_without_retry():
    quota = {"message": "You exceeded your current quota", "type": "insufficient_quota", "code": "insufficient_quota"}
    p, _ = provider(status_error(openai.RateLimitError, 429, {"retry-after": "7"}, body=quota))
    with pytest.raises(errors.JobFailure) as info:
        p.call(REQUEST)
    assert info.value.code == errors.PROVIDER_QUOTA_EXCEEDED and not info.value.retryable
    p, _ = provider(status_error(openai.RateLimitError, 429, body={"code": "rate_limit_exceeded"}))
    with pytest.raises(errors.JobFailure) as info:
        p.call(REQUEST)
    assert info.value.code == errors.TRANSPORT_EXHAUSTED and info.value.retryable


def test_api_schema_keeps_property_names_and_drops_only_listed_keywords():
    contract = stage_schema("analysis")
    stripped = api_schema(contract, ["maxLength"])
    props = stripped["properties"]
    assert "maxLength" not in props["current_visual_description"] and "$schema" not in stripped
    assert set(props) == set(contract["properties"])
    assert stripped["properties"]["context_links"]["maxItems"] == 3

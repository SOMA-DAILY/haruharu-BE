"""strict 구조화 출력이 받아들이는 스키마 키워드를 실제 API 로 확인한다(아주 작은 호출 몇 번)."""
from __future__ import annotations

import json
from typing import Any

from .config import Settings
from .resources import ROOT
from .util import now_utc, iso

KEYWORDS: dict[str, dict[str, Any]] = {
    "maxLength": {"type": "string", "maxLength": 20},
    "minLength": {"type": "string", "minLength": 1},
    "maxItems": {"type": "array", "items": {"type": "string"}, "maxItems": 3},
    "minItems": {"type": "array", "items": {"type": "string"}, "minItems": 0},
    "minimum": {"type": "integer", "minimum": 0},
    "maximum": {"type": "integer", "maximum": 4},
    "format": {"type": "string", "format": "date-time"},
}


def _schema(props: dict[str, dict[str, Any]]) -> dict[str, Any]:
    return {"type": "object", "properties": props, "required": list(props), "additionalProperties": False}


def _try(client: Any, settings: Settings, props: dict[str, dict[str, Any]]) -> tuple[bool, str]:
    import openai
    try:
        response = client.responses.create(
            model=settings.model, instructions="Return one JSON object that satisfies the schema. Use short values.",
            input=[{"role": "user", "content": [{"type": "input_text", "text": "probe"}]}],
            reasoning={"effort": settings.reasoning_effort}, store=False, max_output_tokens=400,
            text={"format": {"type": "json_schema", "name": "probe", "schema": _schema(props), "strict": True}})
        usage = getattr(response, "usage", None)
        return True, f"ok status={response.status} in={getattr(usage, 'input_tokens', 0)} out={getattr(usage, 'output_tokens', 0)}"
    except openai.BadRequestError as error:
        return False, f"400 {str(getattr(error, 'message', ''))[:160]}"


def probe_strict_keywords(settings: Settings, write: bool = False) -> dict[str, Any]:
    from openai import OpenAI
    client = OpenAI(api_key=settings.openai_api_key, timeout=settings.llm_timeout_seconds, max_retries=0)
    calls = []
    ok, detail = _try(client, settings, {f"f_{k}": v for k, v in KEYWORDS.items()})
    calls.append({"keywords": "all", "ok": ok, "detail": detail})
    unsupported: list[str] = []
    if not ok:
        for keyword, prop in KEYWORDS.items():
            single_ok, single_detail = _try(client, settings, {f"f_{keyword}": prop})
            calls.append({"keywords": keyword, "ok": single_ok, "detail": single_detail})
            if not single_ok:
                unsupported.append(keyword)
    result = {"model": settings.model, "probed": True, "probed_at": iso(now_utc()), "unsupported_keywords": unsupported,
              "calls": calls}
    if write:
        (ROOT / "strict_schema_support.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result

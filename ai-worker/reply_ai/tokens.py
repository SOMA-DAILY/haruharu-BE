"""입력 토큰 추정(24,000 토큰 예산 판단·비용 예약용). 실제 사용량은 응답의 usage 로 따로 기록한다."""
from __future__ import annotations

import math
from functools import lru_cache

import tiktoken

MESSAGE_OVERHEAD = 24  # 지시·역할·구조에 붙는 고정 토큰(보수적으로)


@lru_cache(maxsize=1)
def _encoding():
    try:
        return tiktoken.encoding_for_model("gpt-4o")
    except KeyError:  # pragma: no cover - 오래된 tiktoken
        return tiktoken.get_encoding("o200k_base")


def text_tokens(text: str) -> int:
    return len(_encoding().encode(text))


def image_tokens(width: int, height: int, detail: str = "high") -> int:
    """OpenAI 이미지 타일 규칙(high: 2048 안으로 맞춘 뒤 짧은 변 768, 512 타일당 170 + 85)."""
    if detail == "low":
        return 85
    w, h = float(width), float(height)
    fit = min(1.0, 2048 / max(w, h))
    w, h = w * fit, h * fit
    short = min(w, h)
    if short > 768:
        scale = 768 / short
        w, h = w * scale, h * scale
    tiles = math.ceil(w / 512) * math.ceil(h / 512)
    return 85 + 170 * tiles


def estimate_input(instructions: str, payload_text: str, images: list[tuple[int, int, str]] | None = None) -> int:
    total = MESSAGE_OVERHEAD + text_tokens(instructions) + text_tokens(payload_text)
    for width, height, detail in images or []:
        total += image_tokens(width, height, detail)
    return total

"""패키지에 동봉한 계약 스키마·프롬프트·기본 설정(명세 개발 패키지 사본)."""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent / "resources"

CATEGORIES = (
    "question", "reaction", "support", "self_disclosure", "information_suggestion",
    "compliment", "acknowledgment", "humor", "empathy", "greeting",
)
GATE_BOOLEANS = (
    "grounded", "actor_correct", "time_supported", "current_relevance", "conversational_fit",
    "evidence_sufficient", "no_unwarranted_personal_claim", "no_already_answered",
)
GATE_ISSUES = ("none", "unsupported", "actor", "time", "off_topic", "tone", "awkward", "already_answered")
PERSONAL_KINDS = ("direct_request", "shared_dialogue", "explicit_preference", "past_experience", "current_situation")
# 시청자 본인의 사실이어야 하는 개인화 종류(타인 기록을 근거로 쓰면 안 된다).
VIEWER_FACT_KINDS = ("explicit_preference", "past_experience", "current_situation")

# 단계 → (프롬프트 파일, 출력 스키마 파일)
STAGES = {
    "analysis": ("01-analysis.txt", "analysis.schema.json"),
    "generic": ("02-generic.txt", "candidates.schema.json"),
    "personal": ("03-personal.txt", "candidates.schema.json"),
    "gate": ("04-gate.txt", "gate.schema.json"),
    "actual_appropriateness": ("05-actual-appropriateness.txt", "actual-appropriateness.schema.json"),
    "direction_match": ("06-direction-match.txt", "direction-match.schema.json"),
    "propose_patch": ("07-propose-patch.txt", "propose-patch.schema.json"),
    # 패키지에 없던 단계: 13장 고정 검증의 카드 적절성 판정(실제 답장을 숨김). README 참고.
    "card_quality": ("08-card-quality.txt", "card-quality.schema.json"),
}

# version_registry.prompts_json 의 키(단계 이름과 같다).
PROMPT_KEYS = tuple(STAGES)

# 버전 레지스트리에 묶지 않는 보조 단계(지시문은 패키지 파일, sha 를 결과 기록에 남긴다).
# depth_translate: Depth 평가 모델 입력용 영어 변환(공통 맥락·최종 카드 예문).
AUX_STAGES = {
    "depth_translate": ("09-depth-translate.txt", "depth-translate.schema.json"),
}


@lru_cache(maxsize=None)
def schema(name: str) -> dict[str, Any]:
    return json.loads((ROOT / "contracts" / name).read_text(encoding="utf-8"))


@lru_cache(maxsize=None)
def prompt_file(name: str) -> str:
    return (ROOT / "prompts" / name).read_text(encoding="utf-8")


def stage_schema(stage: str) -> dict[str, Any]:
    return schema((STAGES.get(stage) or AUX_STAGES[stage])[1])


# 지시문 파일 안의 제목 예시 자리. 예시 목록의 출처는 config.json generation.title_examples 하나다(코드의 예시 제목 탈락
# 검사와 같은 목록). 버전 지시문(prompts_json)에는 채운 결과가 들어간다.
TITLE_EXAMPLES_PLACEHOLDER = "{{TITLE_EXAMPLES}}"


def title_examples_text(config: dict[str, Any]) -> str:
    """제목 예시를 지시문 문구로: "question '…', empathy '…'"."""
    return ", ".join(f"{e['category']} '{e['title']}'" for e in config["generation"]["title_examples"])


def render_prompt(text: str, config: dict[str, Any]) -> str:
    return text.replace(TITLE_EXAMPLES_PLACEHOLDER, title_examples_text(config)) if TITLE_EXAMPLES_PLACEHOLDER in text else text


def default_prompts() -> dict[str, str]:
    return {stage: render_prompt(prompt_file(files[0]), package_config()) for stage, files in STAGES.items()}


@lru_cache(maxsize=None)
def package_config() -> dict[str, Any]:
    return json.loads((ROOT / "config.json").read_text(encoding="utf-8"))


@lru_cache(maxsize=None)
def strict_support() -> dict[str, Any]:
    """구조화 출력 strict 모드가 받는 스키마 키워드(probe-schema 결과). 없으면 보수적 기본값."""
    path = ROOT / "strict_schema_support.json"
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    return {"unsupported_keywords": ["maxLength", "minLength", "maxItems", "minItems", "minimum", "maximum", "format"],
            "probed": False}

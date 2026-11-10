"""환경 변수 설정. 비밀키 값은 어디에도 출력하지 않는다(repr 에서도 가린다)."""
from __future__ import annotations

import os
import shutil
from dataclasses import dataclass, field
from pathlib import Path

from .resources import package_config


def _bool(name: str, default: bool) -> bool:
    value = os.environ.get(name)
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _float(name: str, default: float) -> float:
    value = os.environ.get(name)
    return float(value) if value not in (None, "") else default


def _int(name: str, default: int) -> int:
    value = os.environ.get(name)
    return int(value) if value not in (None, "") else default


def _list(name: str) -> tuple[str, ...]:
    value = os.environ.get(name, "")
    return tuple(item.strip() for item in value.split(",") if item.strip())


def _tool(name: str, env: str) -> str:
    explicit = os.environ.get(env)
    if explicit:
        return explicit
    for candidate in (f"/opt/homebrew/bin/{name}", shutil.which(name)):
        if candidate and Path(candidate).exists():
            return candidate
    return name


@dataclass(frozen=True)
class Settings:
    database_url: str = ""
    openai_api_key: str = field(default="", repr=False)
    model: str = "gpt-6.1-terra"
    reasoning_effort: str = "low"
    llm_timeout_seconds: float = 90.0
    embedding_url: str = "http://127.0.0.1:18452"
    # local_http(로컬 embedding-serve) | cloudflare_workers_ai(운영: Mac·LM Studio 없이 관리형 API)
    embedding_provider: str = "local_http"
    cloudflare_account_id: str = ""
    cloudflare_api_token: str = field(default="", repr=False)
    embedding_timeout_seconds: float = 30.0
    embedding_concurrency: int = 2
    embedding_max_input_bytes: int = 4096
    media_allowed_prefixes: tuple[str, ...] = ()
    s3_bucket: str = ""
    s3_region: str = "ap-northeast-2"
    daily_budget_usd: float = 10.0
    feedback_budget_usd: float = 10.0
    price_input_per_m: float = 1.0
    price_output_per_m: float = 5.0
    feedback_batch_enabled: bool = False
    automatic_activation_enabled: bool = False
    real_user_feedback_enabled: bool = False
    concurrency: int = 2
    lease_seconds: int = 180
    heartbeat_seconds: int = 30
    poll_seconds: float = 1.0
    batch_check_minutes: int = 60
    input_token_budget: int = 24000
    set_ttl_hours: float = 24.0
    ffmpeg: str = "ffmpeg"
    ffprobe: str = "ffprobe"
    media_max_bytes: int = 50 * 1024 * 1024
    worker_id: str = ""
    # Depth 평가 모델 랭킹(최종 카드 정렬). 끄면 기존 순서·기록과 같다.
    depth_ranking_enabled: bool = False
    depth_url: str = "http://127.0.0.1:18453"
    depth_timeout_seconds: float = 10.0
    depth_context_chat_turns: int = 4      # 공통 맥락 C 에 넣을 인용 단체 대화(최근 N 턴)

    @classmethod
    def from_env(cls) -> "Settings":
        runtime = package_config()["runtime"]
        return cls(
            database_url=os.environ.get("REPLY_AI_DATABASE_URL", ""),
            openai_api_key=os.environ.get("OPENAI_API_KEY", ""),
            model=os.environ.get("REPLY_AI_MODEL", package_config()["model"]["model_id"]),
            reasoning_effort=os.environ.get("REPLY_AI_REASONING_EFFORT", package_config()["model"]["reasoning_effort"]),
            llm_timeout_seconds=_float("REPLY_AI_LLM_TIMEOUT_SECONDS", float(runtime["llm_timeout_seconds"])),
            embedding_url=os.environ.get("REPLY_AI_EMBEDDING_URL", "http://127.0.0.1:18452"),
            embedding_provider=os.environ.get("REPLY_AI_EMBEDDING_PROVIDER", "local_http"),
            cloudflare_account_id=os.environ.get("CLOUDFLARE_ACCOUNT_ID", ""),
            cloudflare_api_token=os.environ.get("CLOUDFLARE_API_TOKEN", ""),
            embedding_timeout_seconds=_float("REPLY_AI_EMBEDDING_TIMEOUT_SECONDS", 30.0),
            embedding_concurrency=_int("REPLY_AI_EMBEDDING_CONCURRENCY", 2),
            embedding_max_input_bytes=_int("REPLY_AI_EMBEDDING_MAX_INPUT_BYTES", 4096),
            media_allowed_prefixes=_list("REPLY_AI_MEDIA_ALLOWED_PREFIXES"),
            s3_bucket=os.environ.get("REPLY_AI_S3_BUCKET", ""),
            s3_region=os.environ.get("REPLY_AI_S3_REGION", "ap-northeast-2"),
            daily_budget_usd=_float("REPLY_AI_DAILY_BUDGET_USD", float(runtime["daily_generation_budget_usd"])),
            feedback_budget_usd=_float("REPLY_AI_FEEDBACK_BUDGET_USD", float(runtime["feedback_budget_usd_per_run"])),
            price_input_per_m=_float("REPLY_AI_PRICE_INPUT_PER_M", 1.0),
            price_output_per_m=_float("REPLY_AI_PRICE_OUTPUT_PER_M", 5.0),
            feedback_batch_enabled=_bool("REPLY_AI_FEEDBACK_BATCH_ENABLED", False),
            automatic_activation_enabled=_bool("REPLY_AI_AUTOMATIC_ACTIVATION_ENABLED", False),
            real_user_feedback_enabled=_bool("REPLY_AI_REAL_USER_FEEDBACK_ENABLED", False),
            concurrency=_int("REPLY_AI_CONCURRENCY", int(runtime["worker_concurrency"])),
            lease_seconds=_int("REPLY_AI_LEASE_SECONDS", int(runtime["lease_seconds"])),
            heartbeat_seconds=_int("REPLY_AI_HEARTBEAT_SECONDS", int(runtime["lease_heartbeat_seconds"])),
            poll_seconds=_float("REPLY_AI_POLL_SECONDS", 1.0),
            batch_check_minutes=_int("REPLY_AI_BATCH_CHECK_MINUTES", int(package_config()["batch"]["check_interval_minutes"])),
            input_token_budget=_int("REPLY_AI_INPUT_TOKEN_BUDGET", int(package_config()["preprocess"]["input_token_budget"])),
            set_ttl_hours=_float("REPLY_AI_SET_TTL_HOURS", runtime["cache_ready_ttl_seconds"] / 3600),
            ffmpeg=_tool("ffmpeg", "REPLY_AI_FFMPEG"),
            ffprobe=_tool("ffprobe", "REPLY_AI_FFPROBE"),
            media_max_bytes=_int("REPLY_AI_MEDIA_MAX_BYTES", 50 * 1024 * 1024),
            worker_id=os.environ.get("REPLY_AI_WORKER_ID", ""),
            depth_ranking_enabled=_bool("REPLY_AI_DEPTH_RANKING_ENABLED", False),
            depth_url=os.environ.get("REPLY_AI_DEPTH_URL", "http://127.0.0.1:18453"),
            depth_timeout_seconds=_float("REPLY_AI_DEPTH_TIMEOUT_SECONDS", 10.0),
            depth_context_chat_turns=max(0, _int("REPLY_AI_DEPTH_CONTEXT_CHAT_TURNS", 4)),
        )

    def max_output_tokens(self, stage: str) -> int:
        caps = package_config()["model"]["max_output_tokens_by_stage"]
        key = {"direction_match": "feedback_judgment", "actual_appropriateness": "feedback_judgment",
               "card_quality": "feedback_judgment"}.get(stage, stage)
        return int(caps.get(key, 4096))


SECRET_NAMES = ("OPENAI_API_KEY", "CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID")


def load_dotenv_secret(path: str | Path, names: tuple[str, ...] = SECRET_NAMES) -> list[str]:
    """지정한 .env 에서 필요한 키만 프로세스 환경으로 옮긴다. 값은 반환·출력하지 않고 옮긴 이름만 돌려준다."""
    loaded = []
    file = Path(path)
    if not file.exists():
        return loaded
    for line in file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip().removeprefix("export ").strip()
        if key in names and not os.environ.get(key):
            os.environ[key] = value.strip().strip('"').strip("'")
            loaded.append(key)
    return loaded

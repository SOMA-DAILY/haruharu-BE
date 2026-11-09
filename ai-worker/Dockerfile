# 영상 답장 추천 AI 작업기(#558). 운영은 ECS Fargate(arm64)에서 돈다 — infra/ai-worker/stack.yml.
# 임베딩은 Cloudflare Workers AI 를 부르므로 모델 가중치(torch)를 넣지 않는다(embedding extra 미설치).
FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
COPY --from=ghcr.io/astral-sh/uv:0.11 /uv /usr/local/bin/uv
WORKDIR /app
COPY pyproject.toml uv.lock README.md ./
COPY reply_ai ./reply_ai
RUN uv sync --frozen --no-dev
ENV PATH="/app/.venv/bin:$PATH" REPLY_AI_FFMPEG=/usr/bin/ffmpeg REPLY_AI_FFPROBE=/usr/bin/ffprobe \
    TIKTOKEN_CACHE_DIR=/app/.tiktoken
# 입력 토큰 추정용 인코딩을 이미지에 미리 받아 둔다(운영 중 외부 저장소에서 받지 않게).
RUN python -c "import tiktoken; tiktoken.get_encoding('o200k_base')" && chmod -R a+rX /app/.tiktoken
USER 10001
CMD ["reply-ai", "worker"]

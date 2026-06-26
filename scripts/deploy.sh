#!/usr/bin/env bash
# EC2 배포 스크립트 — deploy.yml(workflow_dispatch)이 env 를 주입한 뒤 runner 에서 실행한다.
# 동일 env 를 export 하면 로컬에서도 단독 실행/디버깅 가능.
set -euo pipefail

# ── 필수 입력 검증 ──
: "${ENVIRONMENT:?ENVIRONMENT(dev|prod) 필요}"
: "${REPO:?REPO(owner/repo) 필요}"
: "${EC2_HOST:?EC2_HOST 필요}"
: "${EC2_USER:?EC2_USER 필요}"
: "${SSH_KEY:?SSH_KEY(PEM 내용) 필요}"
: "${GHCR_USER:?GHCR_USER 필요}"
: "${GHCR_TOKEN:?GHCR_TOKEN 필요}"
: "${APP_PORT:?APP_PORT(호스트 노출 포트) 필요}"

COMPOSE_FILE="docker-compose.deploy.yml"
DEPLOY_DIR="/home/${EC2_USER}/haruharu"
KEY_FILE="${HOME}/.ssh/deploy_key"

# ── 1. 이미지 태그 · 프로필 결정 (비우면 dev=develop, prod=main) ──
TAG="${IMAGE_TAG:-}"
if [ -z "$TAG" ]; then
  if [ "$ENVIRONMENT" = "prod" ]; then TAG="main"; else TAG="develop"; fi
fi
IMAGE="ghcr.io/$(echo "$REPO" | tr '[:upper:]' '[:lower:]'):${TAG}"
echo "▶ 배포 이미지: ${IMAGE} (profile=${ENVIRONMENT})"

# ── 2. .env 작성 ──
cat > .env <<EOF
SPRING_PROFILES_ACTIVE=${ENVIRONMENT}
APP_IMAGE=${IMAGE}
APP_PORT=${APP_PORT}
DB_URL=${DB_URL:-}
DB_USERNAME=${DB_USERNAME:-}
DB_PASSWORD=${DB_PASSWORD:-}
JWT_SECRET=${JWT_SECRET:-}
KAKAO_CLIENT_ID=${KAKAO_CLIENT_ID:-}
APPLE_CLIENT_ID=${APPLE_CLIENT_ID:-}
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-}
AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-}
AWS_REGION=${AWS_REGION:-ap-northeast-2}
AWS_S3_BUCKET=${AWS_S3_BUCKET:-}
EOF

# ── 3. SSH 키 준비 (keyscan 으로 호스트키 등록 → strict 검증 유지) ──
mkdir -p "${HOME}/.ssh"
printf '%s\n' "$SSH_KEY" > "$KEY_FILE"
chmod 600 "$KEY_FILE"
ssh-keyscan -H "$EC2_HOST" >> "${HOME}/.ssh/known_hosts" 2>/dev/null

ssh_exec() { ssh -i "$KEY_FILE" "${EC2_USER}@${EC2_HOST}" "$@"; }

# ── 4. 파일 전송 & 원격 배포 ──
ssh_exec "mkdir -p '${DEPLOY_DIR}' && chmod 700 '${DEPLOY_DIR}'"
scp -i "$KEY_FILE" .env "$COMPOSE_FILE" "${EC2_USER}@${EC2_HOST}:${DEPLOY_DIR}/"
ssh_exec "chmod 600 '${DEPLOY_DIR}/.env'"
ssh_exec "cd '${DEPLOY_DIR}' \
  && echo '${GHCR_TOKEN}' | docker login ghcr.io -u '${GHCR_USER}' --password-stdin \
  && docker compose -f '${COMPOSE_FILE}' pull \
  && docker compose -f '${COMPOSE_FILE}' up -d \
  && docker logout ghcr.io \
  && docker image prune -f"

# ── 5. 헬스체크 (/actuator/health 가 UP 될 때까지 최대 2분) ──
for i in $(seq 1 12); do
  if ssh_exec "wget -qO- http://localhost:${APP_PORT}/actuator/health" | grep -q '"status":"UP"'; then
    echo "✅ 헬스체크 성공"
    exit 0
  fi
  echo "헬스체크 대기 ${i}/12..."
  sleep 10
done

echo "❌ 헬스체크 실패 — 최근 로그:"
ssh_exec "cd '${DEPLOY_DIR}' && docker compose -f '${COMPOSE_FILE}' logs --tail=80 app"
exit 1

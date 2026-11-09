#!/usr/bin/env bash
# AI 작업기(#558) 배포: 추천 서비스 새 리비전 + 월 1회 개선 루프(batch) 작업 정의 새 리비전.
# 작업기 스택(infra/ai-worker/stack.yml)을 아직 만들지 않은 환경은 건너뛴다 — app·admin 배포를 막지 않는다.
# 월 1회 예약은 batch 가족 이름(리비전 없음)을 가리키므로 새 리비전을 등록하면 다음 실행부터 새 이미지를 쓴다.
#
# 사용: deploy-ai-worker.sh <dev|prod> <image>
set -euo pipefail

ENV="$1"; IMAGE="$2"
CLUSTER="${CLUSTER:-haruharu}"
REGION="${AWS_REGION:-ap-northeast-2}"
OUT="${GITHUB_OUTPUT:-/dev/null}"
if [ "$ENV" = "prod" ]; then
  FAMILY="haruharu-ai-worker"; BATCH_FAMILY="haruharu-ai-worker-batch"
else
  FAMILY="haruharu-ai-worker-dev"; BATCH_FAMILY="haruharu-ai-worker-batch-dev"
fi
SERVICE="haruharu-ai-worker-svc-${ENV}"

STATUS=$(aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" --region "$REGION" \
  --query 'services[0].status' --output text 2>/dev/null || echo "NONE")
if [ "$STATUS" != "ACTIVE" ]; then
  echo "ℹ️ ${SERVICE} 없음(${STATUS}) — AI 작업기 배포 건너뜀"
  echo "deployed=false" >> "$OUT"
  exit 0
fi

bash "$(dirname "$0")/deploy-ecs-service.sh" "$FAMILY" "ai-worker" "$IMAGE" "$SERVICE"

echo "▶ ${BATCH_FAMILY} ← ${IMAGE}"
aws ecs describe-task-definition --task-definition "$BATCH_FAMILY" --region "$REGION" \
  --query 'taskDefinition' > batch-td.json
jq --arg IMAGE "$IMAGE" '
  .containerDefinitions = (.containerDefinitions | map(.image = $IMAGE))
  | del(.taskDefinitionArn, .revision, .status, .requiresAttributes,
        .compatibilities, .registeredAt, .registeredBy)
' batch-td.json > new-batch-td.json
BATCH_ARN=$(aws ecs register-task-definition --cli-input-json file://new-batch-td.json --region "$REGION" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
echo "  registered: ${BATCH_ARN}"

echo "deployed=true" >> "$OUT"
echo "service=${SERVICE}" >> "$OUT"

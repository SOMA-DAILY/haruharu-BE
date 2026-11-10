#!/usr/bin/env bash
# AI 작업기(#558) 배포: 추천 서비스 새 리비전 + 월 1회 개선 루프(batch) 작업 정의 새 리비전.
# 작업기 스택(infra/ai-worker/stack.yml)을 아직 만들지 않은 환경은 건너뛴다 — app·admin 배포를 막지 않는다.
# 월 1회 예약은 batch 가족 이름(리비전 없음)을 가리키므로 새 리비전을 등록하면 다음 실행부터 새 이미지를 쓴다.
# 이미지는 ai-worker 컨테이너만 바꾼다. 추천 태스크의 Depth 사이드카(depth)는 [depth-image] 를 줄 때만 바꾸고, 없으면 지금 이미지를 유지한다.
#
# 사용: deploy-ai-worker.sh <dev|prod> <image> [depth-image]
set -euo pipefail

ENV="$1"; IMAGE="$2"; DEPTH_IMAGE="${3:-}"
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

echo "▶ ${SERVICE} ← ${IMAGE}${DEPTH_IMAGE:+ (depth ← ${DEPTH_IMAGE})}"
aws ecs describe-task-definition --task-definition "$FAMILY" --region "$REGION" \
  --query 'taskDefinition' > worker-td.json
if [ -n "$DEPTH_IMAGE" ] && ! jq -e '.containerDefinitions | any(.name == "depth")' worker-td.json >/dev/null; then
  echo "❌ ${FAMILY} 에 depth 컨테이너가 없다(스택 DepthRanking=false). depth 이미지를 빼고 다시 실행한다" >&2
  exit 1
fi
jq --arg IMAGE "$IMAGE" --arg DEPTH "$DEPTH_IMAGE" '
  .containerDefinitions = (.containerDefinitions | map(
      if .name == "ai-worker" then .image = $IMAGE
      elif (.name == "depth" and $DEPTH != "") then .image = $DEPTH
      else . end))
  | del(.taskDefinitionArn, .revision, .status, .requiresAttributes,
        .compatibilities, .registeredAt, .registeredBy)
' worker-td.json > new-worker-td.json
WORKER_ARN=$(aws ecs register-task-definition --cli-input-json file://new-worker-td.json --region "$REGION" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
echo "  registered: ${WORKER_ARN}"
aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" \
  --task-definition "$WORKER_ARN" --region "$REGION" >/dev/null
echo "  updated service: ${SERVICE}"

echo "▶ ${BATCH_FAMILY} ← ${IMAGE}"
aws ecs describe-task-definition --task-definition "$BATCH_FAMILY" --region "$REGION" \
  --query 'taskDefinition' > batch-td.json
jq --arg IMAGE "$IMAGE" '
  .containerDefinitions = (.containerDefinitions | map(if .name == "ai-worker" then .image = $IMAGE else . end))
  | del(.taskDefinitionArn, .revision, .status, .requiresAttributes,
        .compatibilities, .registeredAt, .registeredBy)
' batch-td.json > new-batch-td.json
BATCH_ARN=$(aws ecs register-task-definition --cli-input-json file://new-batch-td.json --region "$REGION" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
echo "  registered: ${BATCH_ARN}"

echo "deployed=true" >> "$OUT"
echo "service=${SERVICE}" >> "$OUT"

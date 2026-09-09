#!/usr/bin/env bash
# 선택한 이미지 태그로 지정 task def family 의 새 리비전을 등록하고 ECS 서비스를 업데이트한다.
# secrets/환경 등 나머지 컨테이너 정의는 현재 리비전에서 그대로 승계하고 image 태그만 교체한다.
#
# 사용: deploy-ecs-service.sh <family> <container> <image> <service>
#   <container> 가 ALL 이면 모든 컨테이너의 image 를 교체한다 — airflow 태스크처럼 컨테이너 5개가 같은 이미지를 쓰는 경우(#405).
#   CLUSTER, AWS_REGION 은 환경변수로 주입(기본 haruharu / ap-northeast-2).
set -euo pipefail

FAMILY="$1"; CONTAINER="$2"; IMAGE="$3"; SERVICE="$4"
CLUSTER="${CLUSTER:-haruharu}"
REGION="${AWS_REGION:-ap-northeast-2}"

echo "▶ ${SERVICE} ← ${IMAGE} (family=${FAMILY}, container=${CONTAINER})"

# 1) 현재 task def 조회
aws ecs describe-task-definition --task-definition "$FAMILY" --region "$REGION" \
  --query 'taskDefinition' > td.json

# 2) 컨테이너 image 만 교체(ALL 이면 전부) + register 가 거부하는 read-only 필드 제거
jq --arg IMAGE "$IMAGE" --arg C "$CONTAINER" '
  .containerDefinitions = (.containerDefinitions
    | map(if ($C == "ALL" or .name == $C) then .image = $IMAGE else . end))
  | del(.taskDefinitionArn, .revision, .status, .requiresAttributes,
        .compatibilities, .registeredAt, .registeredBy)
' td.json > new-td.json

# 3) 새 리비전 등록
NEW_ARN=$(aws ecs register-task-definition \
  --cli-input-json file://new-td.json --region "$REGION" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
echo "  registered: ${NEW_ARN}"

# 4) 서비스 업데이트(새 리비전으로 롤링 배포)
aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" \
  --task-definition "$NEW_ARN" --region "$REGION" >/dev/null
echo "  updated service: ${SERVICE}"

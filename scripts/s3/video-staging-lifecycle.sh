#!/usr/bin/env bash
# users/video-upload-staging/ 영상 업로드 임시 객체만 3일 후 만료한다.
# 기존 버킷 수명주기 규칙은 보존하고 ID가 같은 규칙만 교체한다.
#
# 사용:
#   scripts/s3/video-staging-lifecycle.sh upsert <bucket> [region]
#   scripts/s3/video-staging-lifecycle.sh check  <bucket> [region]
set -euo pipefail

MODE="${1:-}"
BUCKET="${2:-}"
REGION="${3:-ap-northeast-2}"
RULE_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/video-staging-lifecycle-rule.json"

if [[ "$MODE" != "upsert" && "$MODE" != "check" ]] || [[ -z "$BUCKET" ]]; then
  echo "usage: $0 <upsert|check> <bucket> [region]" >&2
  exit 2
fi
for command in aws jq; do
  command -v "$command" >/dev/null 2>&1 || { echo "required command not found: $command" >&2; exit 2; }
done
jq -e '
  .ID == "expire-video-upload-staging"
  and .Status == "Enabled"
  and .Filter.Prefix == "users/video-upload-staging/"
  and .Expiration.Days == 3
' "$RULE_FILE" >/dev/null

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
CURRENT="$WORK_DIR/current.json"
DESIRED="$WORK_DIR/desired.json"
ERROR="$WORK_DIR/aws-error.txt"

read_configuration() {
  if aws s3api get-bucket-lifecycle-configuration \
      --bucket "$BUCKET" --region "$REGION" --output json >"$CURRENT" 2>"$ERROR"; then
    return 0
  fi
  if grep -q 'NoSuchLifecycleConfiguration' "$ERROR"; then
    printf '{"Rules":[]}\n' >"$CURRENT"
    return 0
  fi
  cat "$ERROR" >&2
  return 1
}

rule_matches() {
  jq -e --slurpfile expected "$RULE_FILE" '
    [.Rules[]? | select(.ID == $expected[0].ID)] as $matches
    | ($matches | length) == 1 and $matches[0] == $expected[0]
  ' "$CURRENT" >/dev/null
}

verify() {
  read_configuration
  if ! rule_matches; then
    echo "FAIL lifecycle rule is missing or different: bucket=$BUCKET region=$REGION" >&2
    return 1
  fi
  echo "PASS lifecycle rule: bucket=$BUCKET region=$REGION id=expire-video-upload-staging expiration=3d"
}

if [[ "$MODE" == "check" ]]; then
  verify
  exit 0
fi

read_configuration
if ! rule_matches \
    && [[ "$(jq '.Rules // [] | length' "$CURRENT")" -gt 0 ]] \
    && [[ -z "$(jq -r '.TransitionDefaultMinimumObjectSize // empty' "$CURRENT")" ]]; then
  echo "FAIL cannot preserve TransitionDefaultMinimumObjectSize; upgrade AWS CLI before changing an existing lifecycle configuration" >&2
  exit 1
fi
jq --slurpfile replacement "$RULE_FILE" '
  {
    Rules: (((.Rules // []) | map(select(.ID != $replacement[0].ID))) + [$replacement[0]])
  }
' "$CURRENT" >"$DESIRED"

if rule_matches; then
  echo "SKIP lifecycle rule already current: bucket=$BUCKET region=$REGION"
else
  PUT_ARGS=(
    s3api put-bucket-lifecycle-configuration
    --bucket "$BUCKET"
    --region "$REGION"
    --lifecycle-configuration "file://$DESIRED"
  )
  TRANSITION_DEFAULT="$(jq -r '.TransitionDefaultMinimumObjectSize // empty' "$CURRENT")"
  if [[ -n "$TRANSITION_DEFAULT" ]]; then
    PUT_ARGS+=(--transition-default-minimum-object-size "$TRANSITION_DEFAULT")
  fi
  aws "${PUT_ARGS[@]}"
  echo "UPDATED lifecycle rule: bucket=$BUCKET region=$REGION"
fi

verify

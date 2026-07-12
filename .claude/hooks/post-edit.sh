#!/usr/bin/env bash
# PostToolUse(Edit|Write) 훅
# 1) .java 수정 파일을 누적 → Stop 훅(stop-gate.sh)에서 일괄 컴파일 게이트
# 2) application*.yaml 시크릿 하드코딩 즉시 감지 (.githooks/pre-commit 로직 재사용, 커밋보다 빠른 피드백)

INPUT=$(cat)
FILE=$(printf '%s' "$INPUT" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))' 2>/dev/null)
[ -n "$FILE" ] && [ -f "$FILE" ] || exit 0

PROJ="${CLAUDE_PROJECT_DIR:-$(pwd)}"
GITDIR=$(git -C "$PROJ" rev-parse --git-dir 2>/dev/null)
[ -n "$GITDIR" ] || exit 0
case "$GITDIR" in /*) ;; *) GITDIR="$PROJ/$GITDIR" ;; esac

case "$FILE" in
  *.java)
    echo "$FILE" >> "$GITDIR/claude-pending-java.list" 2>/dev/null || true
    ;;
esac

case "$FILE" in
  *application*.yml|*application*.yaml)
    SECRETS=$(grep -nE "^[[:space:]]*(password|secret|token|api.?key|credentials|private.?key)[[:space:]]*:" "$FILE" 2>/dev/null \
      | grep -v "^[[:space:]]*#" \
      | grep -v '\${' || true)
    if [ -n "$SECRETS" ]; then
      echo "[secret-scan] $FILE 에 하드코딩된 시크릿 의심 라인 감지:" >&2
      echo "$SECRETS" >&2
      echo "secret-policy: 실제 값 대신 \${ENV_VAR} 플레이스홀더로 교체할 것." >&2
      exit 2
    fi
    ;;
esac

exit 0

#!/usr/bin/env bash
# Stop 훅: 이번 응답에서 .java 수정이 있었으면 컴파일 통과 전까지 완료 선언을 차단한다.
# 실패 시 에러를 stderr로 돌려줘 Claude가 수정 루프를 돌게 한다 (차단보다 피드백).
# 전체 테스트는 여기서 돌리지 않는다 — JVM 특성상 "편집 시 가볍게, Stop에서 컴파일만".

PROJ="${CLAUDE_PROJECT_DIR:-$(pwd)}"
GITDIR=$(git -C "$PROJ" rev-parse --git-dir 2>/dev/null)
[ -n "$GITDIR" ] || exit 0
case "$GITDIR" in /*) ;; *) GITDIR="$PROJ/$GITDIR" ;; esac

LIST="$GITDIR/claude-pending-java.list"
COUNT="$GITDIR/claude-stopgate.count"

[ -s "$LIST" ] || exit 0

cd "$PROJ" || exit 0
OUT=$(./gradlew -q compileJava compileTestJava 2>&1)
STATUS=$?

if [ $STATUS -eq 0 ]; then
  rm -f "$LIST" "$COUNT"
  exit 0
fi

N=$(cat "$COUNT" 2>/dev/null || echo 0)
N=$((N + 1))
echo "$N" > "$COUNT"

if [ "$N" -ge 3 ]; then
  echo "[stop-gate] 컴파일 실패가 ${N}회 연속 — 무한루프 방지를 위해 차단을 해제한다. 컴파일 에러가 남아있으니 수동 확인 필요." >&2
  rm -f "$LIST" "$COUNT"
  exit 0
fi

echo "[stop-gate] .java 수정 후 컴파일 실패 — 완료 선언 전에 아래 에러를 수정할 것:" >&2
ERRORS=$(printf '%s\n' "$OUT" | grep -E "error:|\.java:[0-9]+" | head -30)
if [ -n "$ERRORS" ]; then
  printf '%s\n' "$ERRORS" >&2
else
  printf '%s\n' "$OUT" | tail -30 >&2
fi
exit 2

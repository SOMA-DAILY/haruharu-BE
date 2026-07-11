#!/usr/bin/env bash
# PreToolUse(Edit|Write) 훅: 검증 인프라 약화 차단 (ECC config-protection 패턴)
# 원칙: 검증이 실패하면 설정을 약화시키지 말고 코드를 고친다.
# 보호 대상 수정이 정말 필요하면 사용자가 직접 수정하거나 훅을 일시 해제한다.

INPUT=$(cat)
PARSED=$(printf '%s' "$INPUT" | python3 -c '
import sys, json
d = json.load(sys.stdin)
ti = d.get("tool_input", {})
fp = ti.get("file_path", "")
blob = (ti.get("old_string") or "") + (ti.get("new_string") or "") + (ti.get("content") or "")
print(fp)
print("JACOCO" if "jacoco" in blob.lower() else "")
' 2>/dev/null)
FILE=$(printf '%s\n' "$PARSED" | sed -n 1p)
JACOCO=$(printf '%s\n' "$PARSED" | sed -n 2p)
[ -n "$FILE" ] || exit 0

case "$FILE" in
  *.githooks/*)
    echo "[config-protection] .githooks/ 는 검증 인프라 — 수정 차단. 훅이 실패한다면 코드를 고쳐서 통과시킬 것." >&2
    exit 2
    ;;
  */.claude/settings.json|*/.claude/hooks/*)
    echo "[config-protection] 훅 설정(.claude/settings.json, .claude/hooks/)은 보호 대상 — 사용자가 직접 수정해야 한다." >&2
    exit 2
    ;;
  */build.gradle|build.gradle)
    if [ -n "$JACOCO" ]; then
      echo "[config-protection] build.gradle의 jacoco 관련 변경 감지 — 커버리지 강제를 약화하는 변경은 사용자 승인 필요. 다른 변경이라면 jacoco 블록을 건드리지 않는 방식으로 수정할 것." >&2
      exit 2
    fi
    ;;
esac

exit 0

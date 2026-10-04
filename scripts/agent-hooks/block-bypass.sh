#!/usr/bin/env bash
# PreToolUse(Bash) 훅: 검증 우회 명령 차단
# git 명령 문맥에서만 검사한다 — 이슈 본문·문서에 단어가 등장하는 것은 오탐이므로 잡지 않는다.

INPUT=$(cat)
CMD=$(printf '%s' "$INPUT" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("tool_input",{}).get("command",""))' 2>/dev/null)
[ -n "$CMD" ] || exit 0

# git 명령에 붙은 --no-verify 만 차단 (파이프·세미콜론 뒤는 별도 명령으로 취급)
if printf '%s' "$CMD" | grep -qE 'git[^|;&]*[[:space:]]--no-verify'; then
  echo "[block-bypass] git --no-verify 는 pre-commit 시크릿 감지를 우회한다 — 차단. 훅이 실패하면 원인을 고칠 것." >&2
  exit 2
fi

# git 명령 문맥의 core.hooksPath 변경만 검사. 허용값은 .githooks 뿐 (뒤에 공백·EOL·따옴표·백틱 허용)
if printf '%s' "$CMD" | grep -qE 'git[^|;&]*core\.hooksPath'; then
  if ! printf '%s' "$CMD" | grep -qE 'core\.hooksPath[ =]+"?\.githooks"?([[:space:]]|$|`|")'; then
    echo "[block-bypass] core.hooksPath 변경/해제는 검증 인프라 우회 — 차단. 허용값은 .githooks 뿐." >&2
    exit 2
  fi
fi

exit 0

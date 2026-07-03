#!/usr/bin/env bash
# 스모크 테스트: 서버가 "실제로" 동작하는지 curl로 확인한다.
# 사용법: docker compose up -d && ./gradlew bootRun &  후에  bash scripts/smoke.sh
# 종료코드 0 = 전체 통과. 그 외 = 실패 (실패 항목이 stderr에 출력됨)
set -u
BASE="${SMOKE_BASE_URL:-http://localhost:8080}"
FAIL=0

check() { # 설명, 기대상태코드, URL, [curl 추가 인자...]
  local desc="$1" expect="$2" url="$3"; shift 3
  local code
  code=$(curl -s -o /tmp/smoke_body -w '%{http_code}' --max-time 10 "$@" "$BASE$url")
  if [ "$code" = "$expect" ]; then
    echo "PASS  [$code] $desc"
  else
    echo "FAIL  [got $code, want $expect] $desc — $url" >&2
    head -c 300 /tmp/smoke_body >&2; echo >&2
    FAIL=1
  fi
}

echo "── smoke: $BASE ──"

# 1. 서버 살아있음 + DB 연결 (actuator health)
check "actuator health (서버+DB 기동)" 200 /actuator/health

# 2. health 응답이 실제로 UP 인지 (상태코드만으론 부족)
if ! grep -q '"status":"UP"' /tmp/smoke_body 2>/dev/null; then
  # health를 다시 읽어 body 검증
  BODY=$(curl -s --max-time 10 "$BASE/actuator/health")
  echo "$BODY" | grep -q '"status":"UP"' && echo "PASS  health status UP" || { echo "FAIL  health body: $BODY" >&2; FAIL=1; }
fi

# 3. 공개 엔드포인트 동작 (인증 없이 200)
check "value-questions 공개 조회" 200 /api/v1/value-questions

# 4. Security 필터 살아있음 (보호 엔드포인트는 토큰 없이 401이어야 정상)
check "보호 엔드포인트 무토큰 401" 401 /api/v1/users/me

# 5. swagger 문서 노출
check "swagger UI" 200 /swagger-ui/index.html

# 6. Hurl 케이스 (설치돼 있으면 scripts/smoke/*.hurl 전부 실행)
if command -v hurl > /dev/null 2>&1; then
  for f in scripts/smoke/*.hurl; do
    [ -e "$f" ] || continue
    if hurl --variable base_url="$BASE" --test "$f" > /tmp/hurl_out 2>&1; then
      echo "PASS  hurl: $f"
    else
      echo "FAIL  hurl: $f" >&2; tail -5 /tmp/hurl_out >&2; FAIL=1
    fi
  done
else
  echo "SKIP  hurl 미설치 (선택) — winget install Orange-OpenSource.Hurl 또는 scoop install hurl"
fi

[ "$FAIL" = "0" ] && echo "── smoke: ALL PASS ──" || echo "── smoke: FAILED ──" >&2
exit $FAIL

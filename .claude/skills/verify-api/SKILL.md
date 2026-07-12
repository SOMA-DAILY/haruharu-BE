---
description: PR 전 원커맨드 종합 검증 게이트. 컴파일 → 테스트+커버리지 → 시크릿 스캔 → 서버 기동+smoke → diff 리뷰 순서로 실행하고 고정 리포트를 출력. 기능 구현 완료 후, PR 생성 전, 또는 "검증해줘"·"확인해줘" 요청 시 사용.
argument-hint: "[생략 가능 — 현재 작업 트리 기준]"
allowed-tools: Bash(./gradlew *) Bash(curl *) Bash(bash scripts/smoke.sh) Bash(git status) Bash(git diff *) Bash(grep *) Read Grep Glob
---

## 언제 쓰나?

- `/bdd-implement` 완료 후 ~ `/create-pr` 전 사이 (PR 게이트)
- 긴 세션에서 30분 이상 연속 수정 후 중간 점검
- "검증해줘", "제대로 되는지 확인해줘" 요청 시

## 실행 절차 — 각 Phase 실패 시 즉시 중단·수정 후 처음부터 재실행

### Phase 1. 컴파일
```bash
./gradlew compileJava compileTestJava -q
```

### Phase 2. 테스트 + 커버리지
```bash
./gradlew test jacocoTestReport
```
실패 테스트 0건 확인. 커버리지는 Service·Domain 계층 80% 목표 (`build/reports/jacoco/`).

### Phase 3. 시크릿 스캔
```bash
git diff --name-only origin/develop...HEAD | grep -E "application-(local|staging)|\.env" && echo "BLOCKED" || echo OK
grep -rnE "^\s*(password|secret|token|api.?key)\s*:" --include="application*.yaml" --include="application*.yml" . | grep -v '\${' | grep -v "^\s*#" || echo OK
```

### Phase 4. 실제 동작 검증
```bash
./gradlew bootRun   # 백그라운드 기동 후
bash scripts/smoke.sh
```
신규 엔드포인트는 `curl`로 개별 호출해 시나리오 기대 결과와 대조. **요청/응답 본문을 리포트에 첨부.**

### Phase 5. Diff 리뷰
```bash
git diff origin/develop...HEAD --stat
```
- 이슈와 무관한 파일 변경 없는지 (modification-policy)
- 4계층 위반 의심 시 `java-reviewer` 에이전트 호출

## 출력 형식 (고정)

```
## 검증 리포트

| Phase | 결과 | 비고 |
|-------|------|------|
| 1. 컴파일 | PASS/FAIL | |
| 2. 테스트+커버리지 | PASS/FAIL | N개 통과, 커버리지 X% |
| 3. 시크릿 스캔 | PASS/FAIL | |
| 4. 실제 동작 (smoke) | PASS/FAIL | [요청/응답 증거] |
| 5. Diff 리뷰 | PASS/FAIL | 변경 N파일 |

판정: ✅ READY for PR / ❌ NOT READY — [사유]
```

## Anti-Patterns (금지)

- Phase 일부만 돌리고 "READY" 판정 (전 Phase 결과가 표에 있어야 함)
- FAIL을 "비고"에 적고 READY 판정
- Phase 4를 테스트 통과로 대체 ("테스트 = 실제 동작"이 아님 — 서버 기동·HTTP 호출이 기준)

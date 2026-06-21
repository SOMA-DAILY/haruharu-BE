---
description: BDD 기반 기능 구현 전체 루프. 이슈 시나리오 확인 → 테스트 작성(RED) → 구현(GREEN) → 실제 API 검증(VERIFY). 기능 구현 시작, 이슈 번호 또는 기능 설명을 받아 실행. "구현해줘", "만들어줘", "개발해줘" 요청 시 자동 적용.
argument-hint: "[#이슈번호 또는 기능설명]"
allowed-tools: Bash(./gradlew *) Bash(gh issue view *) Bash(gh issue list *) Bash(git status) Bash(git log *) Bash(git diff *) Read Grep Glob Write Edit WebFetch Agent
---

## 언제 쓰나?

- "구현해줘", "만들어줘", "개발해줘" → **자동 적용**
- 이슈 번호 없으면 → `/create-issue` 먼저 실행 후 복귀
- 새 도메인 필요하면 → Step 2에서 `/new-domain` 먼저 실행

## 실행 절차

### Step 1. 시나리오 확인

이슈 번호가 있으면 GitHub에서 읽는다.

```bash
gh issue view $ARGUMENTS
```

시나리오가 없으면 **중단**하고 사용자에게 `/create-issue`로 먼저 이슈를 작성할 것을 요청한다.

### Step 2. 아키텍처 파악

`.claude/rules/architecture.md`를 참고해 영향 받는 계층을 파악한다.
- 새 도메인이면: `/new-domain {도메인명}` 먼저 실행
- 기존 도메인 수정이면: 해당 파일 목록 확인

### Step 3. 테스트 작성 (RED)

`.claude/rules/testing.md` 기준으로 시나리오를 테스트로 변환한다.

**반드시 작성:**
- Service 단위 테스트 (모든 분기)
- 신규 유저 플로우라면 통합 테스트

```java
@Test
@DisplayName("{시나리오 설명}")
void given{조건}_when{행동}_then{결과}() {
    // given
    // when
    // then
}
```

테스트 실행 후 RED 확인:
```bash
./gradlew test
```

### Step 4. 구현 (GREEN)

`.claude/rules/architecture.md` 패키지 구조와 접미사 컨벤션을 준수한다.
테스트를 통과하는 최소 구현. 과도한 추상화 금지.

```bash
./gradlew test
```

GREEN 확인 후 다음 단계 진행.

### Step 5. 실제 API 검증 (VERIFY)

```bash
./gradlew bootRun
```

서버 기동 후 WebFetch MCP로 실제 HTTP 요청 전송.
응답이 이슈 시나리오의 기대 결과와 일치하는지 확인한다.

**실제 검증 없이 완료 처리 금지.**

### Step 6. 코드 리뷰 (규모에 따라 분기)

변경된 파일 수를 기준으로 판단:

**3개 이상 파일 생성/수정 (새 기능, 새 도메인 포함):**
`java-reviewer` 에이전트로 코드 품질 + 아키텍처 규칙 검증.

```
Agent: java-reviewer
입력: 변경된 파일 경로 목록
```

OAuth2 · JWT · Security 관련 파일이 포함된 경우 추가로:
```
Agent: security-reviewer
입력: 변경된 파일 경로 목록
```

FAIL/CRITICAL 항목은 즉시 수정 후 재검증.

**단순 수정 (메서드 추가, 버그 픽스 등):**
리뷰 에이전트 생략, 바로 Step 7로.

### Step 7. 시크릿 정책 체크

`.claude/rules/secret-policy.md` 체크리스트 확인:
- [ ] 구현 파일에 API 키, 토큰, 비밀번호 등 실제 값 하드코딩 없음
- [ ] 설정값은 `application.yaml`의 `${ENV_VAR}` 플레이스홀더로만 참조
- [ ] `application-local.yaml`, `.env` 파일이 staged에 없음 (`git diff --staged --name-only` 확인)

OAuth2·JWT·PASS 인증 관련 코드라면 `security-reviewer` 에이전트 추가 실행.

### Step 8. 린트 확인

```bash
./gradlew lint
```

### Step 9. 완료 보고

- 생성/수정된 파일 목록
- 테스트 결과
- 실제 API 검증 결과 (요청/응답)
- 리뷰 결과 (실행했다면)
- PR 준비 완료 여부 → `/create-pr` 실행 제안

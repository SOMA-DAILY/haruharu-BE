# Haruharu Backend

## Tech Stack
Java 21 · Spring Boot 4.1.0 · Gradle Groovy DSL · PostgreSQL · JPA + QueryDSL
OAuth2 (Kakao/Apple) · JWT · Spring Security · PASS 지연인증
Base package: `org.son.haruharu`

---

## 작업 실행 게이트 (필수 — 절대 건너뛰지 않는다)

모든 구현 요청은 아래 4단계 게이트를 순서대로 통과한다. 각 단계는 **사용자 승인 후** 다음 단계로 넘어간다.

| 게이트 | 내 행동 | 사용자에게 묻는 것 |
|--------|---------|-----------------|
| 1. 계획 | 의도 파악 → 변경 범위·방법 정리해서 보여줌 | "이슈 생성할까요?" |
| 2. 이슈 | GitHub 이슈 생성 | "구현 시작할까요?" |
| 3. 구현 | BDD 사이클 + 코드 리뷰 | "커밋/PR 할까요?" |
| 4. 커밋·PR | secret-policy 확인 → 커밋 → PR 생성 | — |

**금지 사항:**
- 사용자가 "만들어줘" 라고 해도 계획 없이 바로 코드 작성 금지
- 이슈 없이 구현 시작 금지
- 구현 완료 후 사용자 승인 없이 바로 커밋/PR 금지
- 이전에 승인받은 단계라도 새 대화에선 현재 단계 다시 확인

---

## 상황 → 스킬 라우팅

| 상황 | 실행 | 참고 규칙 |
|------|------|---------|
| 새 기능 / 버그 구현 | `/bdd-implement #이슈번호` | architecture · testing |
| 이슈 없이 "만들어줘" 요청 | `/create-issue` → `/bdd-implement` | bdd-workflow |
| 새 도메인 추가 | `/new-domain {도메인명}` → `/bdd-implement` | architecture |
| PR 생성 | `/create-pr #이슈번호` | git-conventions |
| 빌드/컴파일 실패 | `java-build-resolver` 에이전트 | - |
| OAuth2 · JWT · Security 코드 수정 | `security-reviewer` 에이전트 필수 | - |
| 커밋·PR 생성 전 | `secret-policy` 체크리스트 확인 필수 | secret-policy |

> 이슈 없이 구현 요청 들어오면 **무조건 `/create-issue` 먼저.**

---

## Rules

| 파일 | 적용 시점 |
|------|---------|
| [architecture](.claude/rules/architecture.md) | 파일 생성·수정 시 패키지·계층 확인 |
| [bdd-workflow](.claude/rules/bdd-workflow.md) | 구현 전 BDD 절차 확인 |
| [testing](.claude/rules/testing.md) | 테스트 작성 시 기준 확인 |
| [code-style](.claude/rules/code-style.md) | 네이밍·응답 형식 확인 |
| [git-conventions](.claude/rules/git-conventions.md) | 커밋·브랜치·PR 생성 시 |
| [modification-policy](.claude/rules/modification-policy.md) | 파일 수정 시 항상 — 타인 코드 포함 |
| [secret-policy](.claude/rules/secret-policy.md) | 커밋·PR 생성 전 필수 확인 |
| [exception-handling](.claude/rules/exception-handling.md) | 예외 클래스 추가·수정 시 계층·네이밍 확인 |

---

## 참고 문서

| 용도 | URL |
|------|-----|
| Claude Code Skills 공식 문서 | https://code.claude.com/docs/ko/skills |
| Claude Code Sub-Agents 공식 문서 | https://code.claude.com/docs/ko/sub-agents |
| Claude Code Hooks 가이드 | https://code.claude.com/docs/ko/hooks-guide |
| Claude Code MCP 가이드 | https://code.claude.com/docs/ko/mcp |
| ECC 하네스 레퍼런스 | https://github.com/affaan-m/ECC |

---

## 인증 아키텍처

```
OAuth2 (Kakao/Apple) → 내부 JWT 발급 → Response Body (access_token, refresh_token)
                                       ↓
                             Spring Security Filter
                                       ↓
                          PASS 지연인증 (회원가입 후 선택적)
```

- AccessToken: 단기 만료, Response Body 반환 → 클라이언트가 Authorization 헤더로 전송
- RefreshToken: 장기 만료, Response Body 반환 + DB 저장 (재사용 탐지)

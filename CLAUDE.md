# Haruharu Backend

## Tech Stack
Java 21 · Spring Boot 4.1.0 · Gradle Groovy DSL · PostgreSQL · JPA + QueryDSL
OAuth2 (Kakao/Apple) · JWT HttpOnly Cookie · Spring Security · PASS 지연인증
Base package: `org.son.haruharu`

---

## 상황 → 스킬 라우팅

| 상황 | 실행 | 참고 규칙 |
|------|------|---------|
| 새 기능 / 버그 구현 | `/bdd-implement #이슈번호` | architecture · testing |
| 이슈 없이 "만들어줘" 요청 | `/create-issue` → `/bdd-implement` | bdd-workflow |
| 새 도메인 추가 | `/new-domain {도메인명}` → `/bdd-implement` | architecture |
| PR 생성 | `/create-pr #이슈번호` | git-conventions |

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

---

## 인증 아키텍처

```
OAuth2 (Kakao/Apple) → 내부 JWT 발급 → HttpOnly Cookie
                                       ↓
                             Spring Security Filter
                                       ↓
                          PASS 지연인증 (회원가입 후 선택적)
```

- AccessToken: 단기 만료, HttpOnly Cookie
- RefreshToken: 장기 만료, HttpOnly Cookie + DB 저장

# Haruharu Backend

## Tech Stack
Java 21 · Spring Boot 4.1.0 · Gradle Groovy DSL · PostgreSQL · JPA (Hibernate)
OAuth2 (Kakao/Apple) · JWT · Spring Security · PASS 지연인증
Base package: `org.son.haruharu`

---

## 제품 방향 (2026-08-13 피벗)

**현재 MVP = 운영진 초대코드 모델.** 기존 자동매칭(22:00 배치·남3여3)·IAP 코인·PASS 필수 실명인증 모델은 폐기/보류했다. 새 루프:

운영진이 신청폼으로 모집 → 방 생성 + 초대코드 발급 → 관리자가 설정한 참가자가 코드로 입장 → 1시간 슬롯 영상(3시간 노출)·방 전체 그룹채팅·주기별 미션 → Day3 최종선택(어드민 지정 시각) → 상호 성사 시 1:1 채팅.

- **폐기:** matching(자동매칭) · coin · payment(IAP) · daily_upload_reward
- **보류(비활성 잔존):** identity(PASS → 지연인증) · value(가치관)
- **개편:** room(초대코드·어드민 스케줄·status) · video(슬롯 시간창) · chat(그룹채팅) · mission(활성화)
- **확정 설계 상세:** 노션 "새 MVP 재설계 — 초대코드 모델" + 메모리 `mvp-pivot-invite-code`

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
| PR 전 종합 검증 | `/verify-api` | testing · secret-policy |
| PR 생성 | `/create-pr #이슈번호` | git-conventions |
| 구현 후 코드 품질 검증 (3개 이상 .java 변경) | `java-reviewer` 에이전트 | architecture |
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
                          PASS 지연인증 (보류 — 성별/실명 필요 시점에만 트리거)
```

- AccessToken: 단기 만료, Response Body 반환 → 클라이언트가 Authorization 헤더로 전송
- RefreshToken: 장기 만료, Response Body 반환 + DB 저장 (재사용 탐지)
- 온보딩: **닉네임만** 수집 (성별 미수집 — 필요 시 PASS 지연인증). 옛 프로필 필드(지역·MBTI 등)는 nullable 잔존
- `room_members.gender`는 운영진이 방 구성 시 부여 (admin)

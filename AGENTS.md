# Haruharu Backend Agent Policy

이 파일은 모든 코딩 에이전트의 공통 작업 원칙이다. 상세 지식은 `docs/engineering`, 작업 스킬 원본은 `.agents/skills`에서 관리한다. 도구별 어댑터는 공통 원본을 연결한다.

## 제품과 구조

- 현재 MVP: 운영진이 구성한 참가자가 초대코드로 3일 방에 입장 → 영상·그룹채팅·미션 → 최종 선택 → 상호 성사 시 1:1 채팅.
- 자동 매칭·필수 PASS는 폐기 또는 보류다. 결제 재화는 티켓 한 종류이며(#492) 기본 OFF다. 활성화는 해당 이슈의 명시적 범위일 때만 한다. 세부 맥락은 [product.md](docs/engineering/product.md).
- Java 21, Spring Boot, Gradle 멀티모듈. `app/admin → core`; app과 admin 간 의존은 금지한다.
- 유저 Controller·Request는 app, 공유 business·implement·storage는 core, 어드민 전용 도메인은 admin에 둔다. 도메인 Repository 포트로 DIP를 유지한다.

## 진행과 승인

- 조사·리뷰는 바로 수행한다. 저장소 수정은 이슈 범위와 이슈 브랜치에서 진행한다.
- 이슈가 없는 구현 요청은 짧게 범위·접근법·위험·검증을 정리하고 이슈 생성과 구현 진행을 함께 확인한다. 이미 둘 다 승인했다면 재확인 없이 진행한다.
- 사용자 요청이 이슈 생성에 한정되면 구현까지 확대하지 않는다. 승인한 범위에서는 구현·테스트·결함 수정을 완료까지 이어간다.
- 커밋·푸시·PR은 그 작업에 대한 명시적 승인이 있어야 한다. 구현 승인을 배포·병합 권한으로 확대하지 않는다. 확인된 승인을 같은 범위에서 반복해서 묻지 않는다.
- 기대 행동과 완료 조건을 짧게 정의한다. 중요한 업무 규칙·버그는 red/green TDD를 사용하고, 작은 문서·기계적 변경에 불필요한 테스트를 추가하지 않는다.
- 범위·요구사항·설계 선택을 실질적으로 바꿔야 하면 질문한다. 통상적인 구현 선택과 관련 검증은 자율적으로 진행한다.

## 필요한 문서만 읽기

| 작업 | 공통 문서 |
| --- | --- |
| 패키지·모듈·영속성 구조 | [architecture.md](docs/engineering/architecture.md) |
| 기능·버그 진행과 전문 리뷰 선택 | [bdd-workflow.md](docs/engineering/bdd-workflow.md) |
| 테스트 작성·검사 선택·증거 재사용 | [testing.md](docs/engineering/testing.md) |
| 네이밍·API 응답 | [code-style.md](docs/engineering/code-style.md) |
| 예외·에러 응답·로깅 | [exception-handling.md](docs/engineering/exception-handling.md) |
| 브랜치·이슈·커밋·PR | [git-conventions.md](docs/engineering/git-conventions.md) |
| 수정 범위 판단 | [modification-policy.md](docs/engineering/modification-policy.md) |
| 설정·시크릿·커밋 전 검사 | [secret-policy.md](docs/engineering/secret-policy.md) |

이미 읽은 문서는 관련 내용이 바뀌거나 다른 부분이 필요할 때 다시 읽는다.

## 작업 스킬

필요한 작업에만 사용한다. Codex에서는 `$이름`, Claude Code에서는 `/이름`으로 호출한다.

- [create-issue](.agents/skills/create-issue/SKILL.md): 이슈 생성
- [bdd-implement](.agents/skills/bdd-implement/SKILL.md): 이슈 범위의 구현·검증
- [new-domain](.agents/skills/new-domain/SKILL.md): 새 도메인 구조
- [verify-api](.agents/skills/verify-api/SKILL.md): 변경 범위에 맞는 완료·PR 전 검증
- [create-pr](.agents/skills/create-pr/SKILL.md): 승인된 커밋·푸시·PR 생성

## 검증과 경계

- 관련 테스트부터 실행한다. 코드·테스트·환경이 같은 통과 증거는 재사용하고, 이후 변경의 영향 범위를 재검증한다. 검사 기준과 CI의 전체 검증은 유지한다.
- 변경 결과·검증 명령과 결과·남은 제한을 보고한다. 필요한 검사가 미실행 또는 실패이면 완료로 표시하지 않는다.
- 전문 리뷰는 [작업 위험도](docs/engineering/bdd-workflow.md)에 따라 호출한다. 읽기 전용 리뷰어는 수정하지 않고, CRITICAL/HIGH는 해결·재검토한다.
- 이슈와 무관한 정리나 다른 사람의 변경을 되돌리지 않는다.
- 실패를 통과시키기 위해 테스트·JaCoCo·훅·시크릿 검사를 삭제·비활성화·약화하지 않는다. `git --no-verify`와 `.githooks` 외의 `core.hooksPath` 설정은 금지한다.
- 실제 시크릿·개인 식별자를 추적 파일이나 보고에 노출하지 않는다.

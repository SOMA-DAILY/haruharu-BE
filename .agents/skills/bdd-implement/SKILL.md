---
name: bdd-implement
description: Implement a repository issue with behavior-focused tests and verification appropriate to the change.
---

# 이슈 구현

[행동 중심 개발 흐름](../../../docs/engineering/bdd-workflow.md)을 따른다. 도구는 현재 호스트에서 제공하는 파일·셸·리뷰 도구를 사용한다.

1. `gh issue view <번호>`와 사용자 요청에서 범위·기대 행동을 확인한다. 이슈가 없으면 [create-issue](../create-issue/SKILL.md)를 사용한다. 이미 승인된 이슈 생성·구현은 연속 진행한다.
2. [Git 컨벤션](../../../docs/engineering/git-conventions.md)에 맞는 이슈 브랜치에서 작업한다. 기존 사용자 변경을 보존한다.
3. 영향받는 코드와 필요한 규칙만 읽는다. 새 도메인 구조가 필요하면 [new-domain](../new-domain/SKILL.md)을 사용한다.
4. 중요한 업무 규칙·버그는 red/green TDD를 사용한다. 관련 테스트 클래스·모듈부터 실행하고 실패 이유가 기대 행동과 일치하는지 확인한다. 하네스는 구성·연결·훅 동작을 테스트한다.
5. 구현·관련 테스트·필요한 실제 동작 확인·결함 수정을 완료까지 이어간다. [테스트 기준](../../../docs/engineering/testing.md)에 따라 검사 범위를 정한다.
6. 개발 흐름의 위험도 표에 해당하는 전문 리뷰를 실행하고 발견 사항을 해결한다. 교차 리뷰를 일률적으로 추가하지 않는다.
7. [verify-api](../verify-api/SKILL.md)에서 기존 증거와 최종 변경 상태를 대조하여 빠진 검사를 마친다. 변경 결과·검증·남은 제한을 보고한다.

시나리오의 서식이 빠져 있어도 요구사항이 명확하면 정리하고 진행한다. 요청 범위를 바꾸는 모호함만 질문한다. 커밋·푸시·PR은 [AGENTS.md](../../../AGENTS.md)의 승인 경계를 따른다.

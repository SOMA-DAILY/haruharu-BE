---
name: new-domain
description: Scaffold a new Haruharu domain across the core and app modules or within admin.
---

# 새 도메인 구조

이슈와 구현 승인을 확인하고 [아키텍처](../../../docs/engineering/architecture.md), [코드 스타일](../../../docs/engineering/code-style.md)을 따른다. 기존 도메인에 기능을 추가하는 작업에는 사용하지 않는다.

## 배치

| 역할 | 위치 |
| --- | --- |
| 공유 Service·Command·Response | `core/.../domain/{domain}/business/` |
| 순수 도메인·Repository 포트·Reader·Writer | `core/.../domain/{domain}/implement/` |
| Entity·JpaRepository·RepositoryImpl | `core/.../domain/{domain}/storage/` |
| 유저 Controller·Request | `app/.../domain/{domain}/application/` |
| 어드민 전용 도메인 | `admin/.../admin/domain/{domain}/`의 4계층 |

- base package는 `org.son.haruharu`, admin은 `org.son.haruharu.admin`이다. 주변 도메인의 실제 파일을 참고해 요청 범위에 필요한 구조만 만든다.
- implement의 Repository 포트를 storage가 구현하고 Reader/Writer는 그 포트에 의존한다.
- Entity의 `from()`·`toDomain()` 왕복과 감사 필드 복원을 확인한다. Service가 트랜잭션 경계다.
- 예외가 필요하면 [예외 규칙](../../../docs/engineering/exception-handling.md)을 따른다. 사용하지 않는 빈 디렉터리와 검증 없는 기본 테스트를 만들지 않는다.
- 테스트 위치는 [테스트 기준](../../../docs/engineering/testing.md)과 해당 모듈의 기존 관례로 결정한다.
- `domain-reviewer`에 새 파일과 도메인·모듈 정보를 전달하고 발견 사항을 해결한다. 관련 모듈을 컴파일한다. 동일 상태에서 수행한 검사는 다음 구현·검증 단계에서 재사용한다.
- 구현 흐름에서 호출됐다면 호출한 단계로 복귀한다. 단독 스캐폴딩 요청이면 그 범위에서 마치고, 후속 구현도 승인됐을 때만 [bdd-implement](../bdd-implement/SKILL.md)로 이어간다.

---
name: verify-api
description: Verify a completed repository change or PR readiness using scope-appropriate checks and existing evidence.
---

# 완료·PR 전 검증

[테스트 기준](../../../docs/engineering/testing.md)으로 검사 범위를 정하고 [시크릿 정책](../../../docs/engineering/secret-policy.md)을 적용한다.

## 변경과 증거 확인

- 기준 브랜치와 커밋뿐 아니라 staged·unstaged·이번 작업의 untracked 파일을 포함해 diff를 확인한다. `git diff origin/develop...HEAD`만으로 미커밋 구현을 검증하지 않는다.
- 이미 통과한 검사의 명령·대상·코드 내용·환경을 최종 상태와 대조한다. 유효한 증거를 재사용하고, 이후 변경에 영향받는 검사와 누락된 검사만 실행한다.
- 필요한 전문 리뷰의 완료 여부를 확인한다. 새 위험이나 코드 변경이 없으면 같은 리뷰를 다시 호출하지 않는다.

## 검사 선택

| 변경 | 필요한 확인 |
| --- | --- |
| 일반 문서 | 수정한 내용·링크·예제와 diff 확인 |
| 작업 지침·스킬·리뷰어 문서 | 변경한 참조·스킬 메타데이터·호스트 연결 확인, 절차 변경 시 대표 요청으로 판단 검토 |
| 훅 실행·등록·보호 경로 | 관련 JSON/TOML 파싱·스크립트 실행·허용/차단·호스트 호환성 |
| Java·빌드·런타임 | 관련 테스트, 최종 전체 테스트·커버리지, 변경한 컴파일 대상 |
| API 계약·필터·직렬화·기동·외부 연동 | 관련 통합 테스트와 로컬 서버 HTTP 요청·응답 확인 |
| DB·트랜잭션·동시성 | 실제 PostgreSQL의 관련 제약·롤백·경쟁 조건 테스트 |

Java 전체 검사는 `./gradlew test jacocoTestReport`를 사용한다. 테스트 작업이 필요한 컴파일을 수행하므로 동일 상태에서 별도 compile을 반복할 필요는 없다. CI·JaCoCo·Git 훅의 기존 검증 기준은 유지한다.

HTTP 확인이 필요한 경우 대상 실행 모듈을 선택해 `./gradlew :app:bootRun` 또는 `./gradlew :admin:bootRun`으로 로컬 서버를 실행한다. 현재 MVP와 맞는 `scripts/smoke.sh` 시나리오 및 변경 엔드포인트를 검증한다. 시작한 서버는 확인 후 종료한다. 실제 외부 서비스에 부작용을 주는 호출은 승인된 테스트 환경에서만 수행한다.

훅 또는 하네스 연결을 바꾼 경우의 검사:

```sh
python3 -B .codex/hooks/test_hooks.py
python3 -B scripts/harness/test_harness.py
```

시크릿 검사는 이번 작업의 전체 변경과 커밋 직전 staged 파일을 대상으로 한다. 로컬 자격증명 파일을 출력하거나 실제 토큰·개인정보를 검증 보고에 붙이지 않는다.

## 결과

실행 또는 재사용한 검사·명령, 결과, 코드 상태와 환경, 미해당 이유, 미실행과 제한을 간결하게 보고한다. 필수 검사가 실패하거나 미실행이면 PR 준비 완료로 표시하지 않는다. 실패 시 원인과 영향 범위를 수정·재검증한다.

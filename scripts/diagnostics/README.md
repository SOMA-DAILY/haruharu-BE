# Spring 테스트 컨텍스트 계측 (#564)

`app:test`의 1 GiB 힙, 기본 캐시 상한 32, 단일 test worker를 유지하며 컨텍스트 생성·보유와 힙을 비교한다. 이 디렉터리의 Java listener는 init script를 명시한 실행에만 컴파일·주입된다. 일반 테스트와 CI의 classpath에는 포함되지 않는다.

## 실행

Java 21 및 Docker가 필요하다. 전후 모두 같은 JDK·머신에서 다른 테스트 프로세스 없이 실행하고, 각 실행마다 **새 출력 디렉터리**를 사용한다. 비어 있지 않은 디렉터리는 결과 혼입을 막기 위해 거부한다.

```sh
./gradlew -I scripts/diagnostics/test-context-profile.init.gradle \
  -DtestContextProfileDir=/tmp/haruharu-context-before :app:test

python3 -B scripts/diagnostics/summarize-test-contexts.py \
  /tmp/haruharu-context-before \
  --gc-log /tmp/haruharu-context-before/gc/gc-*.log*
```

변경 후에는 `testContextProfileDir`를 다른 경로로 바꾸어 같은 명령을 실행한다. 요약기가 생성하는 `test-manifest.json`의 클래스·테스트 이름 목록도 비교한다. `--gc-log`에는 **그 실행의 단일 worker PID** 로그와 회전 파일을 모두 전달한다. GC(0)이 없으면 누락된 이력으로 거부한다. 현재 10 MiB × 2의 GC 보존 한도를 넘긴 실행은 전체 GC 합계를 비교할 수 없다.

수집 항목:

- Spring TestContext 생성: TRACE의 `Storing ApplicationContext`에서 고유 컨텍스트 ID를 센다. 캐시 miss 횟수를 생성 수로 간주하지 않는다.
- 최대 캐시 보유: `Spring test ApplicationContext cache statistics`의 `size`. 다른 스레드 풀의 `pool size`는 제외한다.
- Boot 생성·최대 동시 보유: listener의 refresh/close 이벤트. 팬아웃 테스트가 직접 시작하는 두 번째 서버를 포함한다. `ApplicationContextRunner`는 포함하지 않는다. pause 상태여도 close되지 않았다면 보유로 센다.
- 힙: 50 ms 간격 `MemoryMXBean`의 최대 used heap, GC 로그의 Young/Full 직후 최대 heap. 강제 GC는 하지 않는다. listener는 static에 컨텍스트 참조를 저장하지 않는다.
- 시간: `app:test`의 doFirst부터 테스트 JVM 종료·XML 보고 후 doLast까지. 컴파일, Gradle 기동, 결과 복사, JaCoCo 보고 생성 시간은 제외한다.

힙 수치는 RSS·native memory가 아니다. Young/Mixed GC 후 힙에도 아직 회수되지 않은 객체가 있으므로 **생존 객체 크기나 메모리 누수 증거로 해석하지 않는다**. TRACE와 sampler 오버헤드는 전후 동일하게 적용한다. 원본 XML에는 테스트 설정이 들어갈 수 있으므로 외부 공유 전 확인한다.

## 2026-11-18 로컬 비교

기준은 develop `c4b8ff4a`(PR #563 병합), 변경은 #564다. macOS 26.6.2 arm64, Temurin 21.0.11, Gradle 9.5.1, Docker의 PostgreSQL 16·Valkey 8 환경에서 순차 실행했다. 각 단계 1회 관측이며 통계적인 성능 개선율이나 CI 실행 시간으로 일반화하지 않는다.

| 항목 | 변경 전 | 공유 설정 통합만 | 최종: 전용 컨텍스트 종료 포함 |
| --- | ---: | ---: | ---: |
| app 테스트 (실패/오류/스킵) | 1,473 (0/0/0) | 1,473 (0/0/0) | 1,473 (0/0/0) |
| Spring TestContext 생성 | 47 | 45 | 45 |
| 최대 캐시 보유 | 19 | 17 | 12 |
| Boot 생성 (수동 두 번째 서버 포함) | 48 | 46 | 46 |
| 최대 동시 보유 Boot 컨텍스트 | 19 | 17 | 12 |
| 샘플링 최대 힙 (MiB) | 969.82 | 970.03 | 970.26 |
| Young/Full GC 직후 최대 힙 (MiB) | 913 | 870 | 829 |
| Young/Full GC 횟수 | 358 | 310 | 289 |
| Full GC 횟수 | 0 | 0 | 0 |
| Young/Full GC 정지 합계 (ms) | 724.395 | 674.231 | 655.613 |
| app:test (초) | 110.493 | 108.077 | 108.689 |

세 실행의 testcase manifest는 동일했다. 순간 최대 힙은 줄지 않았고, 시간 차이도 작다. 확인된 개선은 중복 생성 2개 제거, 최대 보유 7개 감소, 이 실행에서의 GC 직후 힙과 GC 횟수 감소다. 1 GiB 힙은 유지한다.

### 변경 근거와 경계

1. 기본 H2 컨텍스트의 MockMvc 포함 28개 클래스와 미포함 13개 클래스가 별도 캐시를 사용했다. 13개에도 동일 `@AutoConfigureMockMvc`를 붙여 41개가 공유하도록 했다. 전체 Spring·실제 JPA·기존 서비스 검증은 유지한다.
2. S3 mock 구성이 같은 `ProfileUpsertIntegrationTest`도 MockMvc 설정을 맞춰 `ImageApiIntegrationTest`, `RoomFeedArchiveApiIntegrationTest`와 공유한다. 프로필 테스트는 매번 새 사용자와 프로필 부재를 확인하며 자기 행만 정리한다. 테스트 전체에 트랜잭션을 추가하지 않는다.
3. TRACE에서 각자 단독 캐시 키인 `NotificationCleanupSchedulerToggleTest`, `RecruitDisabledIntegrationTest`, `TicketDisabledIntegrationTest`, `PaymentDisabledIntegrationTest`, `RoomJoinIdentityGateIntegrationTest`는 고유 H2 DB와 `AFTER_CLASS` 종료를 함께 적용한다. 서로 다른 플래그를 한 컨텍스트로 합치지 않는다. `DB_CLOSE_DELAY=0`으로 pool 종료 후 DB도 남기지 않는다.

공유 `haruharu_test` DB에 `DirtiesContext`만 추가하면 Hibernate `create-drop`이 다른 컨텍스트의 스키마를 지울 수 있다. 그래서 수명 종료는 독립 DB를 사용하는 위 5개에만 적용한다. 기본 공유 그룹, PostgreSQL 컨테이너, 수동 두 서버, 전용 Clock·spy 설정은 기존 수명·검증 범위를 유지한다. 앞으로 동일 플래그 컨텍스트를 여러 클래스가 공유하게 되면 종료 정책도 다시 판단한다.

## 실제 CI 반복 실험

`Test context memory experiment` workflow는 `ci-memory-experiment` 라벨을 PR에 붙일 때 명시적으로 실행한다. 기본 브랜치에 등록된 뒤에는 workflow_dispatch도 사용할 수 있다. 문서 수정·일반 push마다 8회 실험을 반복하지 않는다. 일반 CI workflow와 필수 검사는 유지한다.

- 기존 성공 CI 실행 `44260401082`와 같은 Ubuntu 26.04, Temurin 21.0.13+9를 사용한다. 실제 runner 이미지 버전·CPU·RAM·커널·Docker image digest도 artifact에 기록한다.
- A는 `c4b8ff4ab55f929f39a7a0dccfc6d9bd1b60d285`, B는 `52ad9c17639e7f2446a033fd63011ddac6390fa9`의 고정 snapshot이다. 동일 계측 소스를 양쪽에 주입한다.
- 두 독립 VM에서 각각 ABBA와 BAAB를 순차 실행한다. 총 A/B 각 4회다. 같은 VM 안의 차이를 우선 비교하고, 서로 다른 VM의 절대값만 섞어 판단하지 않는다.
- 이미지 pull과 testClasses·계측기 컴파일을 준비 단계에서 수행한다. 각 측정은 새 Gradle/test JVM과 `build --no-daemon`을 사용하며 모든 Test task의 캐시 재사용을 막는다. 매회 기존 XML·GC를 제거하고 admin 208/app 1,473/core 195건과 실제 task 실행 상태를 확인한다.
- app 힙은 설정과 JVM 실측 모두 1 GiB를 확인한다. 다른 JVM의 설정도 전후 동일하게 유지한다.
- 250 ms 간격으로 app test JVM의 `smaps_rollup` RSS/PSS, 전체 Java PSS, VM의 `MemTotal - MemAvailable`을 수집한다. app:test 구간과 전체 build 구간을 별도로 집계한다. 프로세스별 최대값을 더하지 않으며, VM 사용량과 Java PSS를 합산하지 않는다.
- VM 사용량은 Docker·OS까지 포함한 추정치다. PSS는 공유 페이지를 비례 배분한 프로세스 실제 상주 메모리다. 두 지표 모두 샘플 사이의 순간 피크를 보장하지 않는다. idle 기준값과 실제 샘플 간격도 남긴다.
- GC 후 힙의 max/median/p95(최근접 순위), Old region 최대 점유, Full GC·evacuation failure·to-space exhausted를 기록한다. 일반 GC 사유 `Allocation Failure`는 실패로 세지 않는다. OOM 로그, 종료 코드, timeout, host `oom_kill`, runner cgroup의 `memory.events`와 한도를 함께 기록한다. 종료 코드 137만으로 OS OOM을 확정하지 않는다.
- 실행 전후 Java 프로세스·컨테이너 종료를 확인한다. 잔존 작업이 있으면 다음 회차를 오염시키지 않도록 중단한다. 각 회차의 sampler도 새 Python 프로세스여서 결과 파싱 메모리가 다음 회차에 남지 않는다.
- 실패 결과도 수거한다. 잘린 XML/JSONL은 오류로 기록하며 정상 결과로 보정하지 않는다. 개별 실행 20분, 측정 step 90분, job 100분으로 종료·artifact 업로드 여유를 둔다. 강제 중단 시 현재 checkout의 원본 파일도 별도로 업로드한다.

artifact의 `environment.json`, 회차별 `result.json`·`memory.csv`·`tasks.jsonl`·원본 로그, `sequence-result.json`으로 비교한다. 0/4 실패는 해당 조건에서 실패를 관찰하지 않았다는 의미이며 OOM 발생률 감소나 재발 방지를 입증하지 않는다. GC 후 점유 감소가 반복된다면 메모리 압박 완화의 근거로 구분해 보고한다.

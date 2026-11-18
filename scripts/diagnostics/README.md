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

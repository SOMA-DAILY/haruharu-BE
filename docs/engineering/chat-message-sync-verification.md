# #562 채팅 동기화 검증 기록

## 코드와 환경

- 최초 검증일: 2026-11-15, 리뷰 보완: 2026-11-16, Asia/Seoul.
- 기준: `origin/develop`의 `b125aea1`, 작업 브랜치 `feat/562`의 미커밋 #562 구현.
- Java 21.0.11, Gradle 9.5.1, macOS arm64, Docker Desktop의 PostgreSQL 16-alpine / Valkey 8-alpine Testcontainers.
- 로컬 합성 데이터와 test 프로필 사용. 운영 데이터·외부 전송 대상 없이 검증했다.
- 동기화 계약: [chat-message-sync.md](chat-message-sync.md).

## 기능과 회귀

RED: `./gradlew :app:test --tests '*ChatMessageSyncPostgresIntegrationTest'`를 API 구현 전에 실행. `givenNewMessageBetweenPages_whenSync_thenKeepTarget`가 기대 HTTP 200 / 실제 404로 실패했다.

구현 후 관련 검증:

```sh
./gradlew :app:test --tests '*ChatMessageSyncPostgresIntegrationTest' \
  --tests '*ChatMessageSyncFanoutIntegrationTest' \
  :core:test --tests '*ChatMessageReaderSyncTest'
```

동기화 신규 테스트 23건 통과: Reader 10건, PostgreSQL/HTTP 12건, 두 서버 팬아웃 1건. 전체 검증에서도 재실행됐다.

2026-11-15 최초 구현의 전체 검증 명령:

```sh
./gradlew test jacocoTestReport
```

결과: **BUILD SUCCESSFUL**, 3분 27초. 아래 집계는 2026-11-15 실행 결과다. 이후 생성 경로별 경쟁 테스트를 추가했으며, 보완 검증은 다음 절에 별도로 기록한다.

| 모듈 | 테스트 | 실패/오류/스킵 |
| --- | ---: | --- |
| admin | 208 | 0 / 0 / 0 |
| app | 1,465 | 0 / 0 / 0 |
| core | 195 | 0 / 0 / 0 |
| 합계 | 1,868 | 0 / 0 / 0 |

각 모듈의 `build/reports/jacoco/test`에 리포트 생성. 모듈별 LINE 집계는 admin 1,700/1,803, app 1,190/1,370, core 1,133/9,167이다. core의 business 통합 테스트 다수가 app 테스트에서 실행되므로 core 단독 리포트는 그 실행 데이터를 합산하지 않는다. 이 결과를 통합 커버리지 80% 달성으로 해석하지 않는다. 검사·테스트·훅을 비활성화하거나 기준을 낮추지 않았다.

## 2026-11-16 리뷰 보완: 생성 경로별 커밋 경쟁

외부 리뷰의 P2는 확인된 사용자 누락 결함이 아니라, 텍스트 전송끼리만 비교하던 커밋 경쟁 테스트의 검증 공백이다. 프로덕션 코드는 변경하지 않고 `ChatMessageSyncWritePathPostgresTest`와 문서를 보완했다.

- 사진·영상 답장·운영 안내·미션을 각각 텍스트 전송과 양방향으로 조합한 8개 사례를 추가했다. 각 경로가 커밋이 지연되는 첫 저장과 뒤따르는 두 번째 저장을 모두 맡는다.
- 실제 서비스의 저장 후 `TransactionSynchronization.beforeCommit`에서 첫 트랜잭션을 대기시킨다. 미션도 실제 `MissionDispatchStep.send`의 `REQUIRES_NEW` 내부에서 대기하며, 테스트용 외부 트랜잭션으로 경계를 대체하지 않는다.
- 첫 메시지가 다른 연결에서 아직 보이지 않는지, 두 번째 서비스의 실제 DB 세션이 첫 세션을 기다리는지(`pg_backend_pid`, `pg_blocking_pids`) 확인한다. 이때 두 번째 쓰기가 `ChatMessageWriter.save`에 아직 진입하지 않았음을 단언하여 잠금 획득이 IDENTITY 저장보다 앞서는지도 검증한다. 동기화 조회가 이전 커밋 상한을 유지하고, 대기 해제 후 같은 커서에서 두 메시지를 순서대로 모두 복구하는지 검증한다. 미션은 `SENT`와 저장된 메시지 ID도 확인한다.
- 저장과 잠금은 실제 PostgreSQL에서 실행한다. spy는 커밋 대기, 저장 진입과 DB 세션 관측에 사용하고 외부 S3만 mock 처리한다.
- 신규 8건 단독 실행 통과. 이어 테스트의 방 잠금 spy만 일시적으로 우회하는 결함 주입 실험에서 **8건 모두 동기화 상한 검증으로 실패**했다(예: 이전 상한 1, 실제 상한 3). 정상 테스트 파일로 정확히 복구했다. 이 실험은 잠금 누락 회귀의 감지 여부를 확인하며 운영 코드를 변경하지 않았다.
- 후속 리뷰에서 잠금을 저장 뒤로 옮기는 회귀도 확인하도록 저장 미진입 단언을 보완했다. 테스트 내부에서 실제 잠금을 실제 저장 뒤로 늦춘 두 번째 결함 주입 실험에서도 **8건 모두 저장 미진입 단언으로 실패**했고 정상 파일로 복구했다. 읽기 전용 재리뷰 결과 PASS, 남은 지적 없음.

최종 상태의 관련 검증 명령:

```sh
./gradlew :core:test --tests '*ChatMessageReaderSyncTest' \
  :app:test --tests '*ChatMessageSyncPostgresIntegrationTest' \
  --tests '*ChatMessageSyncWritePathPostgresTest' \
  --tests '*ChatMessageSyncFanoutIntegrationTest'
```

결과: **BUILD SUCCESSFUL**, 25초. app 21건(PostgreSQL 12 + 생성 경로 경쟁 8 + 팬아웃 1)은 최종 상태에서 실행했고, 변경 없는 core Reader 10건은 Gradle `UP-TO-DATE`로 기존 통과 증거를 재사용했다. 관련 결과 합계 31건, 실패·오류·스킵 0. 이후 변경은 주석과 문서뿐이다. `git diff --check`와 새 파일의 공백 검사도 통과했다.

전체 1,868건은 이번 보완에서 재실행하지 않았다. 위의 전체 집계와 성능 진단 수치는 최초 구현 시점의 기록이며, 이번 테스트 추가 후 전체 실행·성능 측정을 다시 했다는 의미가 아니다. Gradle의 테스트 결과와 JaCoCo 리포트는 후속 실행 범위로 덮어써질 수 있다.

## 2026-11-17 CI 메모리 부족 보완

- 최초 CI 실행 `43829511201`은 `StompRedisFanoutIntegrationTest`의 Spring 컨텍스트 생성 중 `OutOfMemoryError`가 발생했고, 종료되지 않아 6시간 제한으로 취소됐다. 취소 상태에서는 기존 `if: failure()` 리포트 업로드가 실행되지 않았다.
- Gradle 9.5.1 테스트 worker의 기본 최대 힙은 실제 JVM 인자와 GC 로그에서 **512 MiB**로 확인했다. 앱에는 `@SpringBootTest` 클래스 86개가 있으며, 전체 실행 중 여러 Spring/JPA 컨텍스트가 함께 유지된다. 로컬 클래스 히스토그램에서 컨텍스트/SessionFactory 15개와 23개, 살아 있는 객체 약 343 MiB와 487 MiB를 관측했다. 특정 객체의 누수나 참조 경로까지 확정한 결과는 아니다.
- 기존 설정의 macOS 전체 앱 실행은 통과했지만 Full GC **639회**, Full GC 이후 최대 **509/512 MiB**를 기록했다. 이 실행은 메모리 압박의 재현이며 OOM 자체를 로컬에서 재현했다는 뜻은 아니다. 진단용 임시 init script로 GC 로그와 OOM 종료/덤프 옵션만 추가했고 힙·캐시·테스트 범위는 바꾸지 않았다.
- `app:test`에 최대 힙 **1 GiB**를 지정했다. 테스트 범위, 컨텍스트 캐시, fork 설정과 JaCoCo는 유지한다. OOM이면 테스트 JVM이 즉시 실패 종료하며, 프로세스별 GC 로그를 크기 제한과 함께 `app/build/reports/test-jvm`에 남긴다. CI는 빌드 단계 20분/작업 25분 제한과 실패·취소 시 GC/테스트 리포트 수집을 사용한다. 작업 자체의 강제 종료까지 리포트 업로드를 보장하는 것은 아니다.
- 전체 검증 중 기존 관리자 감사 통계 테스트의 날짜 경계 결함도 확인했다. 현재 시각에서 199분을 빼며 생성한 200건이 자정을 가로질러 하루 200건 조건을 충족하지 못했다. fixture를 고정일 정오로 만들고 조회 기간도 그날로 명시했다. 업무 쿼리와 기존 기대값은 유지했다.

최종 로컬 검증:

```sh
./gradlew :app:build :core:build jacocoTestReport --continue --no-daemon
```

**BUILD SUCCESSFUL**, admin 208 + app 1,473 + core 195 = **1,876건 통과**, 실패·오류·스킵 0. 세 모듈의 테스트가 모두 실행됐고 JaCoCo 리포트가 생성됐다. 관리자 컴파일·bootJar는 앞선 전체 build에서 통과한 동일 프로덕션 코드의 증거를 재사용했다. 수정 후 앱의 Full GC는 **0회**였다. 이는 로컬 테스트 JVM의 진단 수치이며 운영 성능 개선율을 뜻하지 않는다. 빌드 전문 리뷰에서 메모리/CI 설정과 날짜 fixture 수정에 CRITICAL/HIGH 및 명확한 회귀 결함은 없었다. 원격 검증 상태는 PR의 해당 커밋 CI 결과로 확인한다.

## 실제 동작과 측정

두 app 컨텍스트가 동일 PostgreSQL/Valkey를 사용하고, 서로 다른 실제 WebSocket/STOMP 클라이언트 2개가 각각 한 서버에 접속했다. 송신은 실제 ChatService, 방송은 실제 로컬/Redis 어댑터, 복구는 두 번째 서버에 실제 HTTP 요청을 보냈다.

1. A의 Redis 발행 큐를 gate로 지연시켜 A에서는 M1 → M2, B에서는 M2 → M1을 확인했다.
2. B의 Redis 구독만 중단하고 WebSocket은 유지한 상태에서 M3를 저장·발행했다. A는 M3를 받고 B는 받지 못했다.
3. B에서 after=0, size=2로 고정 상한 복구를 실행했다. 각 페이지를 실제 HTTP로 재요청해 중복 응답도 확인했다.
4. ID 기준 병합 모델의 최종 목록과 A의 목록이 같고, 커서는 M3까지 전진했다.

| 진단 | 최종 실행 관측값 |
| --- | --- |
| 장애 구간 커밋 / 복구 페이지 | 3건 / 2페이지 |
| 복구 후 누락 / 중복 행 | 0 / 0 |
| 역순 수신 시나리오 소요 | 93ms |
| 복구 소요(페이지별 재요청 포함) | 77ms |
| 장애 주입 후 정상 순차 전송 | 20건 |
| 정상 순차 전송 호출 시작 → 두 클라이언트 수신 확인 p95 | 18ms |

이는 작은 로컬 기능 진단이다. 서버 기동은 측정에서 제외했고, 정상 순차 전송은 지연 주입 구간과 분리했다. 동시 사용자 부하·지속 처리량·운영 SLO·전후 개선율을 증명하지 않는다. 앱 FE 자체가 아닌 테스트용 병합 모델이므로 실제 앱의 로컬 저장·화면 연동 검증은 별도로 필요하다.

## DB 쿼리와 경쟁 조건

- 같은 방의 5,000건 합성 메시지 중 뒤쪽 구간, pageSize=30, lookahead 포함 최대 31행.
- 권한 검사와 응답 조립을 포함한 동기화 호출 SQL **7회**. 본문이 없는 필터 구간도 커서를 전진시키며 반복하지 않는다.
- 실제 Hibernate 범위 쿼리에 `EXPLAIN (ANALYZE, BUFFERS)` 실행: 기존 `chat_messages_pkey` Index Scan, shared buffer hit=4, execution time=0.019ms. 단일 방에 데이터가 몰린 fixture에서 optimizer가 PK를 선택했다. 기존 `(chat_room_id,id)` 인덱스도 유지했고 새 인덱스를 추가하지 않았다.
- 첫 쓰기를 미커밋 상태로 유지하고 두 번째 쓰기의 `pg_stat_activity.wait_event_type='Lock'`을 확인했다. 그동안 동기화 조회는 이미 커밋된 이전 head만 반환했고, gate 해제 후 두 메시지는 순서대로 복구됐다. 이는 인위적으로 만든 잠금 대기이며 운영 잠금 지연 분포가 아니다.
- 롤백 ID 공백, 페이지 사이 soft delete, 관리자 퇴장 숨김, 다른 방 ID 개입, 빈 방, 정확한 마지막 페이지, 최대 크기, 음수/역전/미래 커서, 매 페이지 멤버십, DIRECT/PAIR 차단·탈퇴를 검증했다.
- 로컬 PostgreSQL IDENTITY 시퀀스 CACHE 1 확인. 생성 경로의 방 잠금은 코드 리뷰에서도 확인했다. 운영 시퀀스 설정은 배포 전 문서의 읽기 전용 SQL로 확인해야 한다.

## 리뷰와 제한

- `java-reviewer`: PASS. CRITICAL/HIGH/MEDIUM 발견 사항 없음. 권한, DIP, 예외, 고정 상한·커서, JPA 가시성, 기존 쓰기 경로의 방 잠금 확인.
- `git diff --check` 통과. #562 소유 변경/새 파일에서 자격증명 패턴 검사 통과. 기존 `.codex/config.toml`, 개인 스킬·캐시, 별도 pseudonym SQL 변경은 수정하지 않았다.
- FE 실제 구현, 운영 DB 설정 확인과 배포는 검증 범위에 포함하지 않았다. 과거 메시지의 상태 변경 전체 복구와 bootstrap 최적화는 계약 문서의 범위 밖 항목이다.

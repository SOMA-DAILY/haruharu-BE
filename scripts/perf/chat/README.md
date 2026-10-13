# GROUP 채팅 로컬 부하 테스트 데이터 (#502)

실제 서비스의 데이터·시크릿을 사용하지 않는 PostgreSQL 16 fixture다. 스키마는 현재 app의 Hibernate 매핑으로 생성하고 PK·UNIQUE·일반 인덱스를 그대로 유지한다. 성능 개선이나 인덱스 효과를 측정한 결과는 아직 아니다.

k6는 서버 구현과 별도로 STOMP 연결·구독·전송·수신을 수행하는 클라이언트 도구다. `rooms` 조회 방식에서 `close_at` 방식으로 바뀌어도 요청 경로와 payload가 같으므로 동일한 부하 시나리오를 재사용한다. 현재 seed/verify SQL은 `close_at` 스키마를 기준으로 한다. 다른 서버 버전을 측정할 때는 해당 버전의 스키마와 데이터 준비 절차를 맞춰야 한다.

기존 `pk-current` 결과는 #502 개발 중 원본 방을 추가 조회하던 미커밋 로컬 구현에서 얻은 값이다. 이전 운영 버전 또는 특정 Git 커밋의 성능 결과로 취급하지 않는다. 이 도구를 포함하는 것이 `close_at` 적용 후 부하 테스트 완료를 의미하지는 않는다. 정식 전후 비교에는 서버 커밋과 변경 상태, 동일한 실행 조건을 기록한 재측정이 필요하다.

## 기본 데이터

| 항목 | 수량 / 조건 |
| --- | --- |
| 소개팅방 / GROUP 채팅방 | 각각 10,000개 |
| ACTIVE / ENDED | 50개 / 9,950개 |
| 사용자 / 방 참여 / 채팅방 참여 | 각각 60,000개, 방당 6명 |
| 과거 TEXT 메시지 | 100,000개, 방당 10개 |
| k6용 활성 사용자 | 300명: 처음 6 / 60 / 180 / 300명을 선택 |
| 종료 방 확인용 사용자 | 별도의 `archived_users` 6명 |
| ACTIVE 방 일정 | 생성 시점 기준 시작 -1일, 선택 시작 +1일, 종료 +2일 |
| 테스트 JWT | 생성 시점부터 24시간, `credentials`로 재발급 |

종료 방도 `chat_room_members.left_at`을 비워 과거 내역 조회가 가능하게 한다. 사용자 프로필·영상·미션·디바이스 토큰은 생성하지 않으며, GROUP 텍스트 채팅과 이력 조회를 위한 데이터다. 가명은 합성 데이터이며 사용자별 JWT subject는 실제 fixture 사용자 ID다.

## 최초 준비

저장소 루트에서 실행한다. 기본 포트는 DB `127.0.0.1:15432`, app `127.0.0.1:18082`다.

```bash
python3 scripts/perf/chat/fixture.py init
docker compose --env-file build/chat-perf/.env -f scripts/perf/chat/compose.yaml up -d --wait
python3 scripts/perf/chat/fixture.py start-app --init-schema
```

마지막 명령은 서버를 foreground로 실행한다. `Started HaruharuApplication`을 확인한 뒤 **다른 터미널**에서 데이터를 넣는다.

```bash
docker exec -i haruharu-chat-perf psql -X -U chat_perf -d haruharu_chat_perf < scripts/perf/chat/seed.sql
docker exec -i haruharu-chat-perf psql -X -U chat_perf -d haruharu_chat_perf < scripts/perf/chat/verify.sql
docker exec -i haruharu-chat-perf psql -X -At -U chat_perf -d haruharu_chat_perf < scripts/perf/chat/export.sql > build/chat-perf/mapping.json
python3 scripts/perf/chat/fixture.py credentials
```

서버가 실행 중일 때 실제 JWT·내역 조회·STOMP 동작을 확인한다.

```bash
k6 run scripts/perf/chat/check-fixtures.js
```

이 명령은 306개 JWT의 이력 조회, 활성 방 메시지 저장·수신, 종료 방 구독 거절을 검증한다. **실행마다 검증용 메시지 1개를 추가한다.** 기능 확인용이므로 출력되는 시간은 부하 테스트 결과로 사용하지 않는다. `verify.sql`은 검증 메시지를 제외한 seed 메시지 10만 건을 검사한다.

seed는 지정 DB·role과 빈 테이블을 검사하고, 전체 삽입을 단일 트랜잭션으로 처리한다. 기존 데이터가 있으면 실패하며 삭제·덮어쓰기하지 않는다. IDENTITY 시퀀스를 맞춘 뒤 `ANALYZE`도 수행한다. 설정 파일이 이미 있으면 `init`도 기존 키를 교체하지 않는다.

규모 변경은 **처음 삽입할 때만** psql에 `-v room_count=100000` 등을 전달한다. 기본값은 `room_count=10000`, `active_rooms=50`, `messages_per_room=10`이다. 10만 방으로 변경하면 사용자 60만 명·메시지 100만 건이 생긴다. 기존 DB 초기화는 자동으로 하지 않는다.

## 이후 사용

DB 컨테이너가 정지되어 있으면 먼저 위의 `docker compose ... up -d --wait`를 실행한다. 서버는 스키마를 수정하지 않는 validate 모드로 기동한다.

```bash
python3 scripts/perf/chat/fixture.py start-app
```

JWT를 새로 발급할 때:

```bash
python3 scripts/perf/chat/fixture.py credentials
```

생성물은 이미 Git에서 제외되는 `build/chat-perf/`에만 저장한다. `.env`, `fixtures.json`은 0600 권한이며 비밀번호·키·JWT를 출력하지 않는다. 이 디렉터리를 포트폴리오에 그대로 업로드하지 않는다. 테스트 서버는 별도 perf 설정을 로드하고 셸의 DB/JWT/AWS/FCM 환경변수를 상속하지 않는다.

`fixtures.json` 구조:

```text
base_url / ws_url       로컬 서버 주소
dataset                seed 시각, 방 수, 방당 참가자·메시지 수
rooms[]                활성 50개 + 종료 1개의 room_id, chat_room_id, 일정
users[]                활성 300명의 user_id, room_id, chat_room_id, access_token
archived_users[]       종료 방 참여자 6명
workloads              6 / 60 / 180 / 300명 구성
issued_at / expires_at  JWT 유효기간 (UTC)
```

`users`는 방·사용자 ID 순으로 정렬되어 있어 처음 N명을 선택하면 방당 6명이 된다. JSON 시각 컬럼은 PostgreSQL `Asia/Seoul`의 로컬 시각이며, JWT 유효기간만 UTC 오프셋을 포함한다.

서버 설정은 단일 인스턴스·Redis OFF·FCM no-op·방 상태 Batch OFF·구독 정리 10초·DB 풀 10개다. 요청별 `close_at` 검증과 구독 정리는 유지되지만 **Batch OFF에서는 예정 시간이 지나도 열린 방을 자동 종료하지 않는다.** 종료 fixture는 close_at을 미리 채운다. 성능 비교의 모든 조건에서 같은 설정을 사용한다. PostgreSQL에는 `pg_stat_statements`를 미리 활성화한다.

## 기존 fixture를 close_at 구현으로 전환

이전 k6 결과는 원본 `rooms` 조회가 포함된 구현의 기준값이다. 현재 구현은 추가 조회를 제거한 비교 대상이므로 결과 라벨을 구분한다. 기존 로컬 app을 Ctrl-C로 종료한 뒤 아래 SQL을 적용한다. seed는 다시 실행하지 않는다.

```bash
docker exec -i haruharu-chat-perf psql -X -v ON_ERROR_STOP=1 -U chat_perf -d haruharu_chat_perf < scripts/sql/502-chat-close-at-up.sql
docker exec -i haruharu-chat-perf psql -X -v ON_ERROR_STOP=1 -U chat_perf -d haruharu_chat_perf < scripts/sql/502-chat-close-at-backfill.sql
```

두 번째 명령을 `UPDATE 0`이 나올 때까지 반복한다(기본 9,950개 종료 방이면 보정 10회 + 완료 확인 1회). 이어 검증하고 같은 풀 크기로 재기동한다.

```bash
docker exec -i haruharu-chat-perf psql -X -v ON_ERROR_STOP=1 -U chat_perf -d haruharu_chat_perf < scripts/sql/502-chat-close-at-verify.sql
docker exec -i haruharu-chat-perf psql -X -U chat_perf -d haruharu_chat_perf < scripts/perf/chat/verify.sql
python3 scripts/perf/chat/fixture.py start-app --pool-size 64
```

다른 터미널에서 먼저 `k6 run scripts/perf/chat/check-fixtures.js`로 기능을 확인한다. 부하 실행은 아래와 같이 라벨만 바꾸고 기존 워밍업·기간·사용자 수를 유지한다.

```bash
k6 run -e USERS=60 -e DB_POOL_SIZE=64 -e LABEL=pool64-close-at-60-r1 scripts/perf/chat/load-test.js
k6 run -e USERS=180 -e DB_POOL_SIZE=64 -e LABEL=pool64-close-at-180-r1 scripts/perf/chat/load-test.js
k6 run -e USERS=300 -e DB_POOL_SIZE=64 -e LABEL=pool64-close-at-300-r1 scripts/perf/chat/load-test.js
```

기존 `pk-current` 파일을 덮어쓰지 않는다. 반복 측정과 같은 풀·데이터·부하 조건을 확인한 뒤 송수신 완결성, rooms 조회 횟수/시간, 지연 p95/p99를 비교한다. 현재 코드만으로 개선율을 확정하지 않는다. 메시지 누적으로 데이터 크기가 달라질 수 있음을 기록한다. 운영 전환 절차는 [GROUP 채팅 종료 문서](../../../docs/engineering/group-chat-lifecycle.md)를 따른다.

서버는 실행한 터미널에서 Ctrl-C로 종료한다. DB만 정지하고 데이터를 유지하려면:

```bash
docker compose --env-file build/chat-perf/.env -f scripts/perf/chat/compose.yaml stop
```

데이터는 `haruharu-chat-perf_data` Docker volume에 남는다. 시작·중지 과정에서 배포, 운영 DB 연결, 인덱스 제거는 수행하지 않는다.

## 실제 STOMP 부하 실행

**발견된 제한:** 기본 DB 풀 10개에서 60명 연결 후 준비 메시지가 전달되지 않는 현상을 재현했다. 스레드 덤프에서 `ChatMessageSentEventListener → ChatDeliveryService(REQUIRES_NEW) → NotificationHistoryService(REQUIRES_NEW)`의 연결 대기를 확인했다. 바깥 트랜잭션 자원이 반환되기 전에 새 연결을 요청하는 구조이며 메시지 하나가 최대 3개 연결을 점유할 수 있다. 기준 인덱스는 이미 존재했다. 이 실패를 인덱스 성능 결과로 사용하지 않는다.

풀 크기에 따른 진단을 계속하려면 실행 중인 서버를 Ctrl-C로 종료하고 다음처럼 **로컬 서버에만** 값을 지정한다.

```bash
python3 scripts/perf/chat/fixture.py start-app --pool-size 64
```

기본 설정 10개는 재현용으로 유지한다. 64는 현재 로컬에서 원인을 분리하기 위한 실험값이며, 중첩 트랜잭션 구조를 해결한 변경이나 운영 권장값이 아니다. 다른 풀 크기의 결과를 같은 인덱스 비교 결과로 합치지 않는다. k6의 `DB_POOL_SIZE`는 결과에 적는 메타데이터일 뿐 서버 풀을 변경하지 않는다.

2026-10-13 로컬 진단에서 풀 64개·60명·워밍업 5초·측정 10초·수신 대기 5초로 전송 120/120건, 수신 720/720건, 누락·중복·오류 0을 확인했다. 송신자 수신 지연 p95는 28ms였으며, 결과는 `build/chat-perf/pool64-diagnose-60-1791896580495.json`에 있다. 이는 준비 단계 실패의 원인을 확인하는 짧은 검증이며, 풀 10개 실패 실행과 측정 시간이 다르므로 지연 개선율이나 지속 부하 처리량을 비교하는 자료로 사용하지 않는다.

위의 서버를 실행한 상태에서 저장소 루트의 다른 터미널을 사용한다. 데이터 준비와 기능 확인을 마쳤다면 `init` / `seed.sql`을 다시 실행하지 않는다.

먼저 6명으로 스크립트가 동작하는지 확인한다.

```bash
k6 run -e USERS=6 -e WARMUP=5 -e DURATION=10 -e DRAIN=5 -e DB_POOL_SIZE=64 -e LABEL=pool64-smoke scripts/perf/chat/load-test.js
```

그다음 기존 인덱스를 유지한 close_at 구현의 성능을 측정한다. 변경 전 결과는 pk-current 라벨로 따로 보관한다.

```bash
k6 run -e USERS=60 -e DB_POOL_SIZE=64 -e LABEL=pool64-close-at-60-r1 scripts/perf/chat/load-test.js
```

기본값은 **워밍업 60초 + 측정 180초 + 잔여 수신 30초**이며 구독 준비 시간은 별도다. 사용자마다 5초에 1건을 전송하므로 60명일 때 측정 구간의 목표는 12건/초, 총 2,160건이다. 각 메시지는 송신자 포함 6명에게 전달되어 총 12,960건의 수신을 기대한다.

같은 방식으로 접속자를 늘린다.

```bash
k6 run -e USERS=180 -e DB_POOL_SIZE=64 -e LABEL=pool64-close-at-180-r1 scripts/perf/chat/load-test.js
k6 run -e USERS=300 -e DB_POOL_SIZE=64 -e LABEL=pool64-close-at-300-r1 scripts/perf/chat/load-test.js
```

**k6 VU 1개 = 방 1개 = 서로 다른 JWT를 사용하는 WebSocket 6개**다. k6 화면에 10 VUs로 표시되어도 `USERS=60`이면 실제 연결은 60개다. 각 방은 준비 메시지를 6명 모두 수신한 뒤 부하를 시작한다. 연결은 측정 내내 유지하며, 응답과 독립적인 일정으로 전송한다. 모든 방의 준비 시각이 완전히 같지는 않으므로 방별 측정 구간이 조금 어긋날 수 있다.

| 출력 지표 | 의미 |
| --- | --- |
| `chat_echo_ms` | 보내기 직전부터 송신자가 자기 메시지를 수신할 때까지의 시간; 측정 구간 메시지만 포함 |
| `chat_peer_ms` | 같은 방 다른 5명의 수신 지연; 모두 같은 k6 프로세스 시계로 측정 |
| `chat_sent` / `chat_delivered` | 측정 구간 전송 / 수신 건수; 수신 건수에는 송신자도 포함 |
| `chat_missing` | 잔여 수신 시간 종료까지 도착하지 않은 측정 구간 메시지의 수신 건수 |
| `chat_duplicates` / `chat_errors` | 중복 수신 / 프로토콜·연결·업무 오류 |
| `chat_send_lag_ms` / `chat_generator_skipped` | 부하 생성기의 전송 일정 지연 / 한 주기 이상 늦어 건너뛴 전송 |

연결 준비·워밍업 메시지는 사용자 정의 지연 통계와 전송·수신 건수에서 제외한다. `ws_msgs_sent` 같은 k6 기본 지표는 STOMP 제어 프레임·준비 메시지·워밍업도 포함하므로 업무 메시지 TPS로 사용하지 않는다. 누락·오류·중복·전송 일정 누락이 있거나 목표 건수를 채우지 못하면 종료 코드가 실패가 된다. 실패했을 때 낮은 p95만 보고 개선으로 판단하지 않는다. 낮은 지연 자체에 임의의 SLO를 적용하지는 않는다.

결과는 `build/chat-perf/<LABEL>-<USERS>-<timestamp>.json`에 자동 저장한다. 이 파일에는 JWT나 비밀번호가 없으며 실행 조건과 k6 요약 지표가 들어 있다. 원본 지표 샘플까지 필요하면 `--out json=build/chat-perf/samples.json`을 추가할 수 있다. 파일 출력 비용도 있으므로 비교 실행에서는 동일하게 설정한다.

## DB 통계와 함께 보기

선택 사항: 측정 직전에 전용 PostgreSQL의 누적 SQL 통계를 초기화한다. 테이블 데이터는 변경하지 않는다.

```bash
docker exec haruharu-chat-perf psql -X -U chat_perf -d haruharu_chat_perf -c 'SELECT pg_stat_statements_reset();'
```

실행 직후 방 관련 SQL 호출 횟수·누적 실행 시간을 저장한다.

```bash
docker exec -i haruharu-chat-perf psql -X -U chat_perf -d haruharu_chat_perf < scripts/perf/chat/stats.sql > build/chat-perf/db-stats.txt
```

DB 통계는 연결 준비·워밍업·측정·수신 대기 및 스케줄러 조회까지 포함한다. 측정 구간만 집계하는 k6 사용자 정의 지표와 구간이 다르므로 SQL 횟수를 `chat_sent`로 나눠 메시지당 정확한 조회 횟수라고 주장하지 않는다. 세부 병목 확인에는 `EXPLAIN (ANALYZE, BUFFERS)`와 잠금 대기도 함께 확인한다.

이번 스크립트는 인덱스를 자동 변경하지 않는다. `pk-current`는 현재 PK·UNIQUE 인덱스가 있는 기준 실행이다. 실제 인덱스 비교는 동일한 데이터 스냅샷·부하·서버 설정으로 조건을 바꾸어 최소 3회씩 실행해야 한다. 이 스크립트는 준비·워밍업·측정 메시지를 DB에 추가하므로 실행 간 데이터를 자동 복원하지 않는다는 점에 유의한다. 로컬 머신에서 서버·DB·k6가 자원을 공유한 결과를 운영 처리 한계로 해석하지 않는다.

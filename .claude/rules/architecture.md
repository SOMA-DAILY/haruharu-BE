---
paths:
  - "**/src/main/java/**"
---

# Architecture: 도메인 기반 4계층

## 모듈 구조 (Gradle 멀티모듈)

`settings.gradle` → `include 'core', 'app', 'admin'`

| 모듈 | 역할 | 산출물 |
|------|------|--------|
| `core` | 공유 라이브러리. 도메인 전체(business/implement/storage) + 공통·설정·인프라 어댑터 | `java-library` (boot jar 아님) |
| `app` | 유저용 API 서버. HTTP·WebSocket(STOMP)·스케줄러·유저 시큐리티 | 실행형 boot jar |
| `admin` | 운영 어드민 서버. core를 공유하되 유저용 웹소켓/스케줄러는 제외 | 실행형 boot jar |

```
app  → core     (implementation project(':core'))
admin → core    (implementation project(':core'))
app  ⇄ admin    금지 — 서로 참조하지 않는다
```

**core에 두지 않는 것 (중요):** `@SpringBootApplication`·스케줄러·웹/시큐리티 필터 빈.
core에 두면 admin에 cron·STOMP가 딸려온다. 이 빈들은 각 실행 모듈(`app`/`admin`)이 가진다.

## 패키지 구조

도메인 우선. 모든 코드는 `domain/{도메인명}/` 하위에 위치한다.
**4계층은 모듈을 가로질러 나뉜다 — `application`은 실행 모듈에, 나머지 3계층은 `core`에 둔다.**

```
core/src/main/java/org/son/haruharu/
├── domain/
│   └── {domain}/                             # ← application 계층 없음
│       ├── business/                         # UseCase 명세 (흐름 조율만)
│       │   ├── {Domain}Service.java          # @Service
│       │   ├── command/                      # 입력 Command
│       │   ├── dto/                          # 출력 Response DTO
│       │   └── exception/                    # 비즈니스 규칙 위반 (extends BusinessException)
│       │
│       ├── implement/                        # 실제 도메인 로직
│       │   ├── {Domain}.java                 # 순수 도메인 객체 (JPA 없음)
│       │   ├── {Domain}Repository.java       # 포트 인터페이스 (도메인 계약, JPA 무관)
│       │   ├── {Domain}Reader.java           # @Component 읽기
│       │   ├── {Domain}Writer.java           # @Component 쓰기
│       │   └── exception/                    # 도메인 불변식 위반 (extends BusinessException)
│       │
│       └── storage/                          # 영속성
│           ├── {Domain}Entity.java           # @Entity + from() / toDomain()
│           ├── {Domain}JpaRepository.java    # JpaRepository<Entity, ID>
│           ├── {Domain}RepositoryImpl.java   # implements {Domain}Repository (DIP 구현체)
│           └── exception/                    # 영속성 조회 실패 (extends BusinessException)
│
├── common/
│   ├── ApiResponse.java · ApiErrorDetail.java
│   ├── crypto/ · entity/ · time/
│   └── exception/
│       ├── BadRequestException.java          # abstract, 400
│       ├── NotFoundException.java            # abstract, 404
│       ├── UnauthorizedException.java        # abstract, 401
│       ├── ForbiddenException.java           # abstract, 403
│       └── ConflictException.java            # abstract, 409
│
├── config/                                   # @ConfigurationProperties·Clock·JPA 등 공유 설정
├── infrastructure/                           # 외부 시스템 어댑터 (oauth·pass·revenuecat·fcm·s3)
└── utils/                                    # JwtProvider·MaskingUtils 등

app/src/main/java/org/son/haruharu/
├── HaruharuApplication.java                  # @SpringBootApplication
├── domain/
│   └── {domain}/
│       └── application/                      # HTTP 진입점 — 이 모듈에는 이 계층만 둔다
│           ├── {Domain}Controller.java       # @RestController
│           └── dto/
│               └── {Action}Request.java      # toCommand() 포함
├── config/                                   # Security·WebSocket·Scheduling·필터·OpenApi
├── handler/                                  # @RestControllerAdvice (GlobalExceptionHandler)
├── infrastructure/scheduler/                 # cron (아직 배치 미전환분 잔존)
├── batch/                                    # Spring Batch(#357). config/=공용 인프라(JDBC JobRepository·스키마), {job}/=Tasklet·JobConfig·@Scheduled 런처
├── demo/                                     # 심사용 데모(#352). prod 노출 O — 어드민 접근 코드로 게이트(@Profile 제한 두지 말 것)
└── devsupport/                               # 개발 편의 API (운영 제외)

admin/src/main/java/org/son/haruharu/admin/
├── AdminApplication.java                     # @SpringBootApplication
├── domain/{account,dashboard,payment,product,room,user}/
│                                             # 어드민 전용 도메인은 4계층 전부 이 모듈에 둔다
├── common/ · config/ · handler/ · security/ · bootstrap/
```

**어느 모듈에 쓸지 판정:**

| 만드는 것 | 위치 |
|-----------|------|
| Controller·Request DTO (유저 API) | `app/domain/{domain}/application/` |
| Service·Command·Response·Reader·Writer·Entity·Repository | `core/domain/{domain}/` |
| 외부 시스템 어댑터(OAuth·S3·FCM…) | `core/infrastructure/` |
| 필터·스케줄러·SecurityConfig·WebSocketConfig | `app/config/`, `app/infrastructure/scheduler/` |
| 배치 잡(Tasklet·JobConfig·런처)·배치 공용 인프라 | `app/batch/{job}/`, `app/batch/config/` |
| 어드민 전용 조회·관리 기능 | `admin/domain/{domain}/` 4계층 전부 |

**어드민 전용 도메인 규칙:** `admin/`은 core 도메인 객체·Entity를 읽어 조인 조회하는 것을 허용한다
(예: `AdminUserQueryRepository`가 `UserEntity`·`CoinWalletEntity`·`RoomMemberEntity`를 함께 조회).
어드민은 여러 도메인을 가로지르는 조회가 본질이므로, core에 어드민 전용 쿼리를 넣지 말고
`admin/domain/{domain}/storage/`에 `Admin*QueryRepository`로 둔다.

## 의존성 방향 (단방향 + DIP)

```
application → business → implement → storage
  (app)         └───────── core ─────────┘
                                  ←  storage (implements {Domain}Repository)
```

모듈이 갈려도 계층 방향은 동일하다. `app`의 Controller는 `core`의 Service만 호출하고,
`core`의 implement/storage를 건너뛰어 직접 주입받지 않는다.

`storage/`가 `implement/`의 Repository 인터페이스를 구현하는 것은 DIP(의존성 역전 원칙)의 올바른 적용이다.
고수준 모듈(implement/도메인)이 인터페이스를 정의하고, 저수준 모듈(storage/인프라)이 구현한다.

```java
// PASS — DIP 적용: implement에 포트 인터페이스, storage가 구현
// implement/UserRepository.java (도메인 계약)
public interface UserRepository {
    Optional<User> findById(Long id);
    User save(User user);
}

// storage/UserRepositoryImpl.java (JPA 어댑터)
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository jpaRepository;

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public User save(User user) {
        return jpaRepository.save(UserEntity.from(user)).toDomain();
    }
}

// implement/UserReader.java — 인터페이스 참조 ✓
@Component
@RequiredArgsConstructor
public class UserReader {
    private final UserRepository userRepository; // 인터페이스 참조

    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(UserNotFoundException::new);
    }
}

// FAIL — implement가 JpaRepository를 직접 참조 (JPA에 결합, DIP 위반)
// implement/UserReader.java
public class UserReader {
    private final UserJpaRepository userJpaRepository; // ✗ JPA 직접 결합
}

// FAIL — business가 storage를 직접 import
// business/UserService.java
public class UserService {
    private final UserJpaRepository userJpaRepository; // ✗ 계층 건너뜀
}
```

## 4계층을 사용하는 이유

Service(business)는 흐름 조율(명세)만 담당한다. 실제 도메인 로직은 implement 계층의 Reader/Writer가 처리한다.

```java
// business/UserService.java — 흐름만 보임, 로직 없음
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserReader userReader;
    private final UserWriter userWriter;

    public UserResponse getUser(Long id) {
        User user = userReader.getById(id);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserCommand command) {
        User user = userWriter.save(command.toDomain());
        return UserResponse.from(user);
    }
}

// implement/UserReader.java — 인터페이스를 통해 읽기
@Component
@RequiredArgsConstructor
public class UserReader {
    private final UserRepository userRepository;

    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(UserNotFoundException::new);
    }
}
```

## @Transactional 경계

- Service 클래스: `@Transactional(readOnly = true)` 기본
- 쓰기 Service 메서드: `@Transactional` 개별 오버라이드
- Reader / Writer: 트랜잭션 없음 — Service가 경계

## Entity ↔ Domain 변환

변환 메서드는 Entity에만 존재한다.

```java
@Entity @Table(name = "users")
public class UserEntity {
    public static UserEntity from(User user) { ... }
    public User toDomain() { ... }
}
```

**왕복이 맞아야 한다 — `toDomain()`이 읽는 필드는 `from()`도 복원한다.**
특히 `BaseEntity`의 감사 컬럼(`createdAt`)을 도메인 객체가 보유한다면 `from()`에서 반드시 세팅한다.

```java
// PASS — 왕복 성립: from(x).toDomain() 이 x와 같은 createdAt을 갖는다
public static UserEntity from(User user) {
    var entity = new UserEntity();
    entity.id = user.id();
    entity.status = user.status();
    entity.createdAt = user.createdAt();   // ← BaseEntity의 protected 필드
    return entity;
}

// FAIL — toDomain()은 createdAt을 읽는데 from()이 안 넣음
public static UserEntity from(User user) {
    var entity = new UserEntity();
    entity.id = user.id();
    entity.status = user.status();
    return entity;                          // createdAt = null
}
```

빠뜨려도 DB는 안 깨진다(`@Column(updatable = false)`라 UPDATE SQL에서 제외). 대신 **`save()`가 돌려주는
객체의 `createdAt`이 null이 되고, 호출부가 넘긴 인자는 조용히 버려진다** — 코드만 봐서는 알 수 없는 죽은 코드가 된다.
실제로 `ChatService.sendMessage`는 `save()` 반환값의 `createdAt`을 STOMP 브로드캐스트 `sentAt`으로 내보낸다(#290).

> 회귀 방지: `EntityAuditColumnRoundTripTest`가 대상 엔티티의 왕복을 검증한다.
> 도메인 객체에 `createdAt`을 추가하는 엔티티가 생기면 그 테스트에도 케이스를 추가한다.

## JPA 성능 패턴 (storage 계층 작성 시)

- **연관관계 기본 LAZY.** 컬렉션 `FetchType.EAGER` 금지 — N+1의 주범.
- **N+1 해소 순서**: ① 읽기 전용 조회는 DTO/인터페이스 프로젝션 ② 엔티티가 필요하면 `JOIN FETCH` 또는 `@EntityGraph`.
- **목록 API는 `Pageable` 필수.** 무한 스크롤·대량 순회는 오프셋 대신 커서(`WHERE id > :lastId ORDER BY id LIMIT n`).
- **변경 `@Query`에는 `@Modifying` + 트랜잭션 필수.**
- **대량 저장**은 `saveAll` + `hibernate.jdbc.batch_size` 설정 확인.
- **인덱스**: 자주 필터하는 컬럼·FK·복합 조건(`status, created_at`)은 `@Table(indexes=...)` 또는 마이그레이션 SQL로 명시.
- SQL 효율 검증은 `hibernate.SQL=DEBUG` 로그로 실제 쿼리 수를 확인한다.

## 도메인 경계

`core/src/main/java/org/son/haruharu/domain/` 아래에 도메인별로 나눈다. **2026-08-13 피벗 기준 상태** (상세: 메모리 `mvp-pivot-invite-code`):

**활성 (새 MVP 루프):**
- `user` — users. 사용자 존재(id, status)
- `auth` — user_credentials · refresh_tokens. OAuth 연결·JWT 세션
- `profile` — user_profiles · user_images. 온보딩=닉네임·지역 (옛 필드 nullable 잔존)
- `agreement` — user_agreements. 약관 동의
- `room` — rooms(초대코드·어드민 스케줄·status) · room_members · room_invites(신규) · final_selections · match_results
- `video` — video_posts(슬롯 시간창) · video_reactions
- `chat` — chat_rooms(GROUP/DIRECT) · chat_room_members · chat_messages
- `mission` — missions(카탈로그) · room_missions(인스턴스)
- `notification` — device_tokens · notification_settings · notification_history (FCM)
- `report` — reports
- `demoaccess` — demo_access_codes. 심사용 데모 로그인 접근 코드 보관(#352, inert — core는 읽지도 분기도 안 함)

**보류 (비활성 잔존, 삭제 X):**
- `identity` — user_identities. PASS 실명인증 → 지연인증(성별/실명 필요 시점에만)
- `value` — value_questions · user_value_answers. 가치관(MVP 온보딩 제외)

**폐기 (새 MVP 비활성/제거 대상):**
- `matching`(자동매칭) · `coin` · `payment`(IAP)

**도메인 간 호출 방향:**
- `auth/` → OAuth 로그인 시 userId 확인 후 `user/UserReader.getById()` 위임
- `profile/` → user_id 외래키로 연결되지만 도메인 간 직접 의존 금지; Service 레벨에서 조율

**금지:**
- `user/`에 user_credentials, user_profiles 등을 직접 추가하지 않는다.
- 도메인 간 Storage 계층 직접 참조 금지 (예: auth 도메인이 UserJpaRepository를 직접 주입받는 것).

## 외부 시스템 (OAuth 등)

포트 인터페이스는 `core/src/main/java/org/son/haruharu/infrastructure/` 안에서 정의하고 구현한다.
`app`/`admin`이 공유해야 하므로 실행 모듈에 두지 않는다. (현재: `oauth` · `pass` · `revenuecat` · `fcm` · `s3`)

```java
// core: infrastructure/oauth/KakaoOAuthPort.java
public interface KakaoOAuthPort {
    KakaoUserInfo getUserInfo(String authCode);
}

// infrastructure/oauth/KakaoOAuthAdapter.java
@Component
public class KakaoOAuthAdapter implements KakaoOAuthPort { ... }
```

## Controller → Service 흐름

Controller는 `app`(또는 `admin`), Service는 `core`에 있다. 모듈이 다를 뿐 호출 형태는 동일하다.

```java
// app: domain/auth/application/AuthController.java
@PostMapping("/login")
public ApiResponse<TokenResponse> login(@RequestBody KakaoLoginRequest request) {
    return ApiResponse.ok(authService.loginWithKakao(request.toCommand()));
}
```

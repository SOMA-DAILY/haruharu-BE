---
paths:
  - "**/src/main/java/**"
---

# Architecture: 도메인 기반 4계층

## 패키지 구조

도메인 우선. 모든 코드는 `domain/{도메인명}/` 하위에 위치한다.

```
src/main/java/org/son/haruharu/
├── domain/
│   └── {domain}/
│       ├── application/                      # HTTP 진입점
│       │   ├── {Domain}Controller.java       # @RestController
│       │   └── dto/
│       │       └── {Action}Request.java      # toCommand() 포함
│       │
│       ├── business/                         # UseCase 명세 (흐름 조율만)
│       │   ├── {Domain}Service.java          # @Service
│       │   ├── command/                      # 입력 Command
│       │   ├── dto/                          # 출력 Response DTO
│       │   └── exception/                   # 비즈니스 규칙 위반 (extends BusinessException)
│       │
│       ├── implement/                        # 실제 도메인 로직
│       │   ├── {Domain}.java                 # 순수 도메인 객체 (JPA 없음)
│       │   ├── {Domain}Repository.java       # 포트 인터페이스 (도메인 계약, JPA 무관)
│       │   ├── {Domain}Reader.java           # @Component 읽기
│       │   ├── {Domain}Writer.java           # @Component 쓰기
│       │   └── exception/                   # 도메인 불변식 위반 (extends BusinessException)
│       │
│       └── storage/                          # 영속성
│           ├── {Domain}Entity.java           # @Entity + from() / toDomain()
│           ├── {Domain}JpaRepository.java    # JpaRepository<Entity, ID>
│           ├── {Domain}RepositoryImpl.java   # implements {Domain}Repository (DIP 구현체)
│           └── exception/                   # 영속성 조회 실패 (extends BusinessException)
│
├── common/
│   └── exception/
│       ├── BadRequestException.java          # abstract, 400
│       ├── NotFoundException.java            # abstract, 404
│       ├── UnauthorizedException.java        # abstract, 401
│       ├── ForbiddenException.java           # abstract, 403
│       └── ConflictException.java            # abstract, 409
│
├── infrastructure/                           # 외부 시스템 어댑터 (OAuth 등)
├── config/
├── handler/                                  # @RestControllerAdvice (GlobalExceptionHandler)
└── utils/
```

## 의존성 방향 (단방향 + DIP)

```
application → business → implement → storage
                                  ←  storage (implements {Domain}Repository)
```

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

## JPA 성능 패턴 (storage 계층 작성 시)

- **연관관계 기본 LAZY.** 컬렉션 `FetchType.EAGER` 금지 — N+1의 주범.
- **N+1 해소 순서**: ① 읽기 전용 조회는 DTO/인터페이스 프로젝션 ② 엔티티가 필요하면 `JOIN FETCH` 또는 `@EntityGraph`.
- **목록 API는 `Pageable` 필수.** 무한 스크롤·대량 순회는 오프셋 대신 커서(`WHERE id > :lastId ORDER BY id LIMIT n`).
- **변경 `@Query`에는 `@Modifying` + 트랜잭션 필수.**
- **대량 저장**은 `saveAll` + `hibernate.jdbc.batch_size` 설정 확인.
- **인덱스**: 자주 필터하는 컬럼·FK·복합 조건(`status, created_at`)은 `@Table(indexes=...)` 또는 마이그레이션 SQL로 명시.
- SQL 효율 검증은 `hibernate.SQL=DEBUG` 로그로 실제 쿼리 수를 확인한다.

## 도메인 경계 (확정 2026-06-21)

ERD 테이블을 관심사 기준으로 5개 최상위 도메인으로 분리한다.

```
domain/
├── user/        ← users 테이블만. 사용자 존재(id, status) 담당.
├── auth/        ← user_credentials, refresh_tokens.
│                   OAuth 공급자 연결, JWT 세션 관리 담당.
├── profile/     ← user_profiles, user_images.
│                   데이팅 프로필(nickname, mbti 등) 및 이미지 담당.
├── identity/    ← user_identities.
│                   PASS 실명인증(CI/DI, 이름, 생년월일) 담당.
└── value/       ← value_questions, user_value_answers.
                    가치관 질문 및 유저 답변 담당.
```

**도메인 간 호출 방향:**
- `auth/` → OAuth 로그인 시 userId 확인 후 `user/UserReader.getById()` 위임
- `profile/` → user_id 외래키로 연결되지만 도메인 간 직접 의존 금지; Service 레벨에서 조율

**금지:**
- `user/`에 user_credentials, user_profiles 등을 직접 추가하지 않는다.
- 도메인 간 Storage 계층 직접 참조 금지 (예: auth 도메인이 UserJpaRepository를 직접 주입받는 것).

## 외부 시스템 (OAuth 등)

포트 인터페이스는 `infrastructure/` 안에서 정의하고 구현한다.

```java
// infrastructure/oauth/KakaoOAuthPort.java
public interface KakaoOAuthPort {
    KakaoUserInfo getUserInfo(String authCode);
}

// infrastructure/oauth/KakaoOAuthAdapter.java
@Component
public class KakaoOAuthAdapter implements KakaoOAuthPort { ... }
```

## Controller → Service 흐름

```java
@PostMapping("/login")
public ApiResponse<TokenResponse> login(@RequestBody KakaoLoginRequest request) {
    return ApiResponse.ok(authService.loginWithKakao(request.toCommand()));
}
```

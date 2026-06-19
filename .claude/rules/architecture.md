# Architecture: 도메인 기반 4계층

## 패키지 구조

도메인 우선. 모든 코드는 `domain/{도메인명}/` 하위에 위치한다.

```
src/main/java/org/son/haruharu/
├── domain/
│   └── {domain}/
│       ├── application/                    # HTTP 진입점
│       │   ├── {Domain}Controller.java     # @RestController
│       │   └── dto/
│       │       └── {Action}Request.java    # toCommand() 포함
│       │
│       ├── business/                       # UseCase 명세 (흐름 조율만)
│       │   ├── {Domain}Service.java        # @Service
│       │   ├── command/                    # 입력 Command
│       │   ├── dto/                        # 출력 Response DTO
│       │   └── exception/                 # 비즈니스 규칙 위반
│       │
│       ├── implement/                      # 실제 도메인 로직
│       │   ├── {Domain}.java               # 순수 도메인 객체 (JPA 없음)
│       │   ├── {Domain}Reader.java         # @Component 읽기
│       │   ├── {Domain}Writer.java         # @Component 쓰기
│       │   └── exception/                 # 도메인 불변식 위반
│       │
│       └── storage/                        # 영속성
│           ├── {Domain}Entity.java         # @Entity + from() / toDomain()
│           └── {Domain}JpaRepository.java  # JpaRepository<Entity, ID>
│
├── infrastructure/                         # 외부 시스템 어댑터 (OAuth 등)
├── config/
├── handler/                                # @RestControllerAdvice
└── utils/
```

## 의존성 방향 (단방향)

```
application → business → implement → storage
```

각 계층은 오른쪽 계층에만 의존한다. 역방향 의존 절대 금지.

```java
// FAIL — implement에 Repository 인터페이스 정의, storage가 구현 → 역방향 발생
// implement/UserRepository.java
public interface UserRepository { ... }
// storage/UserRepositoryImpl.java
public class UserRepositoryImpl implements UserRepository { ... }
// ↑ storage → implement 역방향 의존

// PASS — implement가 storage JpaRepository 직접 사용 (단방향)
// implement/UserReader.java
@Component
@RequiredArgsConstructor
public class UserReader {
    private final UserJpaRepository userJpaRepository; // storage 직접 참조 ✓

    public User getById(Long id) {
        return userJpaRepository.findById(id)
            .map(UserEntity::toDomain)
            .orElseThrow(() -> new UserNotFoundException("id=" + id));
    }
}
```

## 4계층을 사용하는 이유

Service(business)는 흐름 조율(명세)만 담당한다. 실제 도메인 로직은 implement 계층의 Reader/Writer가 처리한다.

```java
// business/UserService.java — 흐름만 보임, 로직 없음
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserReader userReader;
    private final UserWriter userWriter;

    public UserResponse getUser(Long id) {
        User user = userReader.getById(id);
        return UserResponse.from(user);
    }
}

// implement/UserReader.java — 실제 읽기 로직
@Component
@RequiredArgsConstructor
public class UserReader {
    private final UserJpaRepository userJpaRepository;

    public User getById(Long id) {
        return userJpaRepository.findById(id)
            .map(UserEntity::toDomain)
            .orElseThrow(() -> new UserNotFoundException("id=" + id));
    }
}
```

## Entity ↔ Domain 변환

변환 메서드는 Entity에만 존재한다.

```java
@Entity @Table(name = "users")
public class UserEntity {
    public static UserEntity from(User user) { ... }
    public User toDomain() { ... }
}
```

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

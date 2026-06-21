# Code Style

## 네이밍 규칙

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스/인터페이스/Enum | PascalCase | `UserService`, `AuthRepository` |
| 메서드/필드/변수 | camelCase | `findByEmail`, `refreshToken` |
| 상수 | SCREAMING_SNAKE_CASE | `MAX_LOGIN_ATTEMPTS` |
| 패키지 | 소문자 | `com.haruharu.auth.business` |
| 테스트 메서드 | `given{조건}_when{행동}_then{결과}` | `givenExpiredToken_whenRefresh_thenThrows` |

## 클래스 접미사 컨벤션

| 접미사 | 역할 | 위치 |
|--------|------|------|
| `Controller` | HTTP 진입점 | `application/` |
| `Request` | HTTP 요청 DTO (`toCommand()` 포함) | `application/dto/` |
| `Service` | UseCase 명세 (흐름 조율) | `business/` |
| `Command` | 서비스 입력 객체 | `business/command/` |
| `Response` | 서비스 출력 DTO | `business/dto/` |
| `Reader` | 읽기 협력자 | `implement/` |
| `Writer` | 쓰기 협력자 | `implement/` |
| `JpaRepository` | JPA 저장소 인터페이스 | `storage/` |
| `Entity` | JPA 엔티티 | `storage/` |

## 예외 클래스 위치

| 위치 | 예외 유형 | 예시 |
|------|-----------|------|
| `business/exception/` | 비즈니스 규칙 위반 | `UserAlreadyExistsException` |
| `implement/exception/` | 도메인 불변식 위반 | `InvalidNicknameException` |
| `storage/exception/` | 영속성 조회 실패 | `UserNotFoundException` |

- 모든 커스텀 예외: `RuntimeException` 상속
- 메시지에 식별자 포함: `"User not found: id=" + id`

## API 응답 공통 래퍼

`common/ApiResponse.java` + `common/ApiErrorDetail.java` 조합.

```java
// 성공: error 필드 직렬화 제외 (@JsonInclude(NON_NULL))
// 실패: error 필드에 중첩 객체 포함
public record ApiResponse<T>(
        @JsonProperty("isSuccess") boolean isSuccess,
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) ApiErrorDetail error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ApiErrorDetail error) {
        return new ApiResponse<>(false, null, error);
    }
}

public record ApiErrorDetail(String code, String message) {}
```

`GlobalExceptionHandler`에서는 `ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage()))` 패턴을 사용한다.

## 불변성

- 필드는 `final` 기본, 변경 필요 시만 mutable
- 도메인 객체 수정: 새 인스턴스 반환 (`toUpdated()` 패턴)
- JPA Entity만 setter 허용 (JPA 요구사항)

## 생성자 주입

```java
// GOOD — 테스트 가능, @Autowired 불필요
public class UserService {
    private final UserReader userReader;
    private final UserWriter userWriter;

    public UserService(UserReader userReader, UserWriter userWriter) {
        this.userReader = userReader;
        this.userWriter = userWriter;
    }
}
```

필드 주입(`@Autowired`) 사용 금지.

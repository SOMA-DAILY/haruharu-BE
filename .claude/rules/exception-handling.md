# Exception Handling

## 계층 구조

HTTP 시맨틱 기반 추상 예외 클래스를 공통으로 두고, 도메인 예외가 이를 상속한다.

```
common/exception/
├── BadRequestException      # abstract, 400
├── NotFoundException         # abstract, 404
├── UnauthorizedException     # abstract, 401
├── ForbiddenException        # abstract, 403
└── ConflictException         # abstract, 409
        ↑ extends
domain/{domain}/{layer}/exception/
└── {Domain}{Reason}Exception.java   # 도메인별 구체 예외
```

## 도메인 예외 클래스 패턴

생성자에서 에러코드(SCREAMING_SNAKE_CASE)와 메시지를 고정한다. 서비스에서 메시지를 입력할 필요 없다.

```java
// domain/user/storage/exception/UserNotFoundException.java
public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException() {
        super("USER_NOT_FOUND", "존재하지 않는 사용자입니다.");
    }
}

// domain/user/business/exception/UserAlreadyExistsException.java
public class UserAlreadyExistsException extends ConflictException {
    public UserAlreadyExistsException() {
        super("USER_ALREADY_EXISTS", "이미 존재하는 사용자입니다.");
    }
}
```

## 에러코드 네이밍: SCREAMING_SNAKE_CASE

```
// GOOD
USER_NOT_FOUND
USER_ALREADY_EXISTS
INVALID_NICKNAME

// BAD — 숫자 코드 방식 (D001, U001 등) 사용 금지
D001, U001, A002
```

에러코드는 도메인명 + 이유로 구성한다. 예: `{DOMAIN}_{REASON}`.

## 서비스 사용 패턴

```java
// GOOD — 이름 있는 예외 클래스를 throw만
throw new UserNotFoundException();
throw new UserAlreadyExistsException();

// BAD — 직접 메시지 입력
throw new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
```

## GlobalExceptionHandler 처리 범위

| 예외 | HTTP 상태 | 로그 레벨 |
|------|---------|---------|
| `BadRequestException` | 400 | `warn` |
| `NotFoundException` | 404 | `warn` |
| `UnauthorizedException` | 401 | `warn` |
| `ForbiddenException` | 403 | `warn` |
| `ConflictException` | 409 | `warn` |
| `MethodArgumentNotValidException` | 400 | 없음 |
| `ConstraintViolationException` | 400 | `warn` |
| `MethodArgumentTypeMismatchException` | 400 | `warn` |
| `HttpMessageNotReadableException` | 400 | `warn` |
| `HttpRequestMethodNotSupportedException` | 405 | `warn` |
| `NoResourceFoundException` | 404 | `warn` |
| `DataIntegrityViolationException` | 409 | `warn` |
| `Exception` (catch-all) | 500 | `error` (스택 포함) |

핸들러에는 로깅을 넣지 않는다. 로깅은 Filter 또는 Interceptor로 중앙화한다.
- `log.error`는 catch-all `Exception` 핸들러에만 허용 (예상치 못한 오류만)
- 4xx 핸들러에 `log.warn` 추가 시 이후 AOP/Filter 도입 시 이중 로깅 발생

> 향후 HTTP 레벨 전체 로깅(200·4xx·5xx)은 `OncePerRequestFilter` 또는 `HandlerInterceptor`로 구현한다.
> AOP는 HTTP 상태코드를 직접 알 수 없어 이 목적에 적합하지 않다.

## API 응답 형식

**성공**
```json
{
  "isSuccess": true,
  "data": { ... }
}
```

**실패** (`isSuccess == false`일 때만 `error` 필드 존재)
```json
{
  "isSuccess": false,
  "data": null,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "존재하지 않는 사용자입니다."
  }
}
```

## 예외 위치 규칙

도메인 내 어느 계층에서 발생하는 예외든 해당 계층의 `exception/` 패키지에 정의한다.

| 위치 | 예외 유형 | 예시 |
|------|-----------|------|
| `business/exception/` | 비즈니스 규칙 위반 | `UserAlreadyExistsException extends ConflictException` |
| `implement/exception/` | 도메인 불변식 위반 | `InvalidNicknameException extends BadRequestException` |
| `storage/exception/` | 영속성 조회 실패 | `UserNotFoundException extends NotFoundException` |

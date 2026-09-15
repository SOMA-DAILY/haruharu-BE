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
| `MethodArgumentNotValidException` | 400 | `warn` (필드명만 — 값은 PII) |
| `ConstraintViolationException` | 400 | `warn` |
| `MethodArgumentTypeMismatchException` | 400 | `warn` (파라미터명만) |
| `MissingServletRequestParameterException` | 400 | `warn` |
| `HttpMessageNotReadableException` | 400 | `warn` (파싱 원문 제외 — 요청 바디는 PII) |
| `HttpRequestMethodNotSupportedException` | 405 | `warn` |
| `NoResourceFoundException` | 404 | `warn` |
| `DataIntegrityViolationException` | 409 | `warn` (**제약명만** — 아래 참조) |
| `BadGatewayException` | 502 | `error` (스택 포함) |
| `DataAccessException` | 503 | `error` (스택 포함) |
| `Exception` (catch-all) | 500 | `error` (스택 포함) / 클라 끊김은 `debug` |

## 로깅 책임 분담 (#257, #259)

실패 로깅은 **핸들러가 담당한다.** Filter는 응답 status(403)만 볼 뿐 *왜* 403인지 모르므로,
도메인 에러코드는 예외 객체에 접근할 수 있는 핸들러에서만 남길 수 있다.

| 무엇 | 어디 |
|------|------|
| traceId·userId | `MdcLoggingFilter` (MDC → 모든 로그에 자동 부착) |
| access log (method·path·status·duration) | `AccessLogFilter` |
| 실패 원인 (도메인 에러코드) | `GlobalExceptionHandler` |
| 인증 실패 사유 (401) | `SecurityConfig`의 EntryPoint — 핸들러에 도달하지 않는다 |
| 인가 실패 (403) | `JwtAuthenticationFilter.writeError` — 핸들러에 도달하지 않는다 |

access log와 실패 원인은 관심사가 달라 **이중 로깅이 아니라 상보 관계다.**
레벨 기준: 4xx = 유저 귀책·재시도 가능 → `warn`(스택 없음) / 5xx = 서버 귀책 → `error`(스택 포함).

## 로그에 남기지 않는 것 (#259)

- **요청 본문·쿼리스트링** — PII가 섞인다
- **토큰 원문** — 401 로그에는 사유(`BAD_SIGNATURE`·`EXPIRED`·`MALFORMED`)만 남긴다
- **DB 예외 메시지 원문** — pgjdbc는 `logServerErrorDetail` 기본값이 true라 서버 에러의 DETAIL
  (`Key (token)=(실제 FCM 토큰) already exists`)이 예외 메시지에 실린다.
  `DataIntegrityViolationException` 핸들러는 **제약명만** 추출하고,
  `application.yaml`의 `logServerErrorDetail: false`와 두 겹으로 막는다.

> 인증 실패 **사유는 로그에만** 남기고 클라이언트 응답은 "인증이 필요합니다."로 뭉뚱그린다.
> 사유를 응답으로 노출하면 계정 열거·토큰 대조에 악용된다.

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

package org.son.haruharu.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.common.ApiErrorDetail;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.common.exception.BadGatewayException;
import org.son.haruharu.common.exception.BadRequestException;
import org.son.haruharu.common.exception.ConflictException;
import org.son.haruharu.common.exception.ForbiddenException;
import org.son.haruharu.common.exception.NotFoundException;
import org.son.haruharu.common.exception.UnauthorizedException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.DisconnectedClientHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 전역 예외 → API 응답 변환 + 실패 로깅(#257).
 *
 * 로깅 책임 분담:
 * - traceId·userId : MdcLoggingFilter 가 MDC 에 심어 모든 로그에 자동 부착
 * - access log(method·path·status·duration) : AccessLogFilter 담당(#259).
 *   원래 ALB access log 몫으로 봤으나 실제 ALB 는 접근로그가 꺼져 있어 200 이 아무 데도 남지 않았다.
 * - 실패 원인(도메인 에러코드) : 여기. 예외 객체에 접근할 수 있는 유일한 지점이라
 *   Filter 로는 대체할 수 없다 — Filter 는 응답 status(403)만 볼 뿐 왜 403 인지 모른다.
 *   access log 와는 관심사가 달라 이중 로깅이 아니라 상보 관계다.
 *
 * 레벨 기준: 4xx=유저 귀책·재시도 가능 → WARN(스택 없음) / 5xx=서버 귀책 → ERROR(스택 포함).
 * 요청 본문·쿼리스트링은 PII 가 섞일 수 있어 남기지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * PostgreSQL 제약 위반 메시지에서 제약명만 뽑는다: ... violates unique constraint "ux_device_tokens_token"
     * violates 절에 앵커링한다 — 그냥 constraint "..." 로 두면 DETAIL 의 컬럼 값 안에 같은 문구가 있을 때
     * 그 조각을 제약명으로 오인해 값 일부가 로그로 샌다.
     */
    private static final Pattern CONSTRAINT_NAME_PATTERN =
            Pattern.compile("violates (?:unique|foreign key|check|exclusion|not-null) constraint \"([^\"]+)\"");

    /** 4xx 공통. 스택은 남기지 않는다 — 유저 귀책이라 노이즈만 되고 코드로 원인이 특정된다. */
    private void warn(int status, HttpServletRequest request, String code, String message) {
        log.warn("{} {} {} code={} msg={}",
                status, request.getMethod(), request.getRequestURI(), code, message);
    }

    /** 5xx 공통. 스택을 남긴다 — 서버 귀책이라 원인 추적이 필요하다. */
    private void error(int status, HttpServletRequest request, String code, Exception e) {
        log.error("{} {} {} code={}",
                status, request.getMethod(), request.getRequestURI(), code, e);
    }

    // ===================== 도메인 예외 (4xx) =====================

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException e, HttpServletRequest request) {
        warn(400, request, e.getCode(), e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException e, HttpServletRequest request) {
        warn(404, request, e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e, HttpServletRequest request) {
        warn(401, request, e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException e, HttpServletRequest request) {
        warn(403, request, e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException e, HttpServletRequest request) {
        warn(409, request, e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    // ===================== 프레임워크 예외 (4xx) =====================
    // 도메인 예외와 같은 기준으로 남긴다 — FE 연동 시 "왜 400인지"를 서버 로그로 답할 수 있어야 한다.

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e,
                                                              HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("잘못된 요청입니다.");
        // 어느 필드가 틀렸는지는 필드명만 남긴다(값은 PII 가능).
        String fields = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("-");
        warn(400, request, "INVALID_INPUT", message + " fields=" + fields);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("INVALID_INPUT", message)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e,
                                                                       HttpServletRequest request) {
        warn(400, request, "INVALID_INPUT", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("INVALID_INPUT", "잘못된 요청 파라미터입니다.")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                                HttpServletRequest request) {
        warn(400, request, "INVALID_TYPE", "param=" + e.getName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("INVALID_TYPE", "잘못된 타입의 요청입니다.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e,
                                                                    HttpServletRequest request) {
        warn(400, request, "MISSING_PARAMETER", "param=" + e.getParameterName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("MISSING_PARAMETER",
                        "필수 요청 파라미터가 누락되었습니다: " + e.getParameterName())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e,
                                                               HttpServletRequest request) {
        // 파싱 실패 원문은 요청 본문이 섞이므로 남기지 않는다(PII).
        warn(400, request, "INVALID_REQUEST_BODY", "요청 바디 파싱 실패");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("INVALID_REQUEST_BODY", "잘못된 요청 바디입니다.")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                                      HttpServletRequest request) {
        warn(405, request, "METHOD_NOT_ALLOWED", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(new ApiErrorDetail("METHOD_NOT_ALLOWED", "허용되지 않는 HTTP 메서드입니다.")));
    }

    /** 정적 리소스·오타 경로. 봇 스캔으로도 발생하므로 WARN 이되 메시지는 최소화한다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e,
                                                              HttpServletRequest request) {
        warn(404, request, "NOT_FOUND", "매핑 없음");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(new ApiErrorDetail("NOT_FOUND", "요청한 경로를 찾을 수 없습니다.")));
    }

    /**
     * unique 제약 위반 등. 앱이 막지 못한 동시성·중복이라 WARN 으로 남기고 원인(제약명)을 함께 본다.
     * 예: 같은 기기로 다른 계정 로그인 시 ux_device_tokens_token 충돌.
     *
     * 예외 메시지 원문은 남기지 않는다(#259) — pgjdbc 는 logServerErrorDetail 기본값이 true 라
     * DETAIL("Key (token)=(실제 FCM 토큰) already exists")이 메시지에 실린다.
     *
     * 단 이 방어는 <b>이 핸들러 경로에만</b> 미친다 — handleDataAccess·캐치올은 풀스택을 남기고
     * 스케줄러 스레드엔 이 핸들러가 닿지 않는다. 그 경로들은 application.yaml 의
     * logServerErrorDetail=false 에 단독 의존하므로 그 설정을 지우면 조용히 재발한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e,
                                                                 HttpServletRequest request) {
        warn(409, request, "DATA_INTEGRITY_VIOLATION", "constraint=" + extractConstraintName(e));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(new ApiErrorDetail("DATA_INTEGRITY_VIOLATION", "중복된 데이터가 존재합니다.")));
    }

    /** 제약명만 화이트리스트로 추출한다. 못 찾으면 원문 대신 "-" — 원문에는 컬럼 값이 섞여 있다. */
    private String extractConstraintName(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause == null) {
            return "-";
        }
        Matcher matcher = CONSTRAINT_NAME_PATTERN.matcher(cause);
        return matcher.find() ? matcher.group(1) : "-";
    }

    // ===================== 5xx =====================

    @ExceptionHandler(BadGatewayException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadGateway(BadGatewayException e, HttpServletRequest request) {
        error(502, request, e.getCode(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    // DB 연결 실패, 쿼리 타임아웃, 데드락 등 인프라 장애 (DataIntegrityViolationException은 더 구체적이므로 위 핸들러가 우선)
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException e, HttpServletRequest request) {
        error(503, request, "DATABASE_ERROR", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(new ApiErrorDetail("DATABASE_ERROR", "데이터베이스 오류가 발생했습니다.")));
    }

    /**
     * 캐치올. 클라이언트 연결 종료(탭 닫기·앱 이탈)는 서버 버그가 아니라서 ERROR 로 남기지 않는다(#259) —
     * 그대로 두면 유저가 탭을 닫을 때마다 ERROR 기준 알람이 울린다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
            // 이미 닫힌 소켓이라 응답 바디는 나가지 않는다. 로그만 남기고 조용히 끝낸다.
            log.debug("클라이언트 연결 종료 {} {}", request.getMethod(), request.getRequestURI());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(new ApiErrorDetail("INTERNAL_SERVER_ERROR", "알 수 없는 에러가 발생했습니다.")));
        }
        error(500, request, "INTERNAL_SERVER_ERROR", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(new ApiErrorDetail("INTERNAL_SERVER_ERROR", "알 수 없는 에러가 발생했습니다.")));
    }
}

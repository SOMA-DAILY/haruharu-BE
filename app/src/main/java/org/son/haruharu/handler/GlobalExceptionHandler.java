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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 유저 귀책 4xx는 WARN으로 남긴다 — 문의 대응 시 재현 없이 원인을 특정하기 위함.
     * userId 외의 식별자(providerId 등)나 요청 본문은 PII라 남기지 않는다.
     */
    private void warn4xx(int status, HttpServletRequest request, String code, String message) {
        log.warn("{} {} {} code={} userId={} msg={}",
                status, request.getMethod(), request.getRequestURI(), code, currentUserId(), message);
    }

    /** 인증 전(401)이거나 필터를 타지 않은 요청이면 "-". principal은 JwtAuthenticationFilter가 넣은 userId(Long). */
    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            return "-";
        }
        return userId.toString();
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException e, HttpServletRequest request) {
        warn4xx(400, request, e.getCode(), e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException e, HttpServletRequest request) {
        warn4xx(404, request, e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e, HttpServletRequest request) {
        warn4xx(401, request, e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException e, HttpServletRequest request) {
        warn4xx(403, request, e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException e, HttpServletRequest request) {
        warn4xx(409, request, e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(BadGatewayException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadGateway(BadGatewayException e) {
        log.error("Upstream service error: {}", e.getCode(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("INVALID_INPUT", message)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("INVALID_INPUT", "잘못된 요청 파라미터입니다.")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("INVALID_TYPE", "잘못된 타입의 요청입니다.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("MISSING_PARAMETER",
                        "필수 요청 파라미터가 누락되었습니다: " + e.getParameterName())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("INVALID_REQUEST_BODY", "잘못된 요청 바디입니다.")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(new ApiErrorDetail("METHOD_NOT_ALLOWED", "허용되지 않는 HTTP 메서드입니다.")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(new ApiErrorDetail("NOT_FOUND", "요청한 경로를 찾을 수 없습니다.")));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(new ApiErrorDetail("DATA_INTEGRITY_VIOLATION", "중복된 데이터가 존재합니다.")));
    }

    // DB 연결 실패, 쿼리 타임아웃, 데드락 등 인프라 장애 (DataIntegrityViolationException은 더 구체적이므로 위 핸들러가 우선)
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException e) {
        log.error("DataAccessException", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(new ApiErrorDetail("DATABASE_ERROR", "데이터베이스 오류가 발생했습니다.")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(new ApiErrorDetail("INTERNAL_SERVER_ERROR", "알 수 없는 에러가 발생했습니다.")));
    }
}

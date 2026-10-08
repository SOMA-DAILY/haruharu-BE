package org.son.haruharu.admin.handler;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.common.ApiErrorDetail;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.common.exception.BadRequestException;
import org.son.haruharu.common.exception.ConflictException;
import org.son.haruharu.common.exception.FieldValidationException;
import org.son.haruharu.common.exception.ForbiddenException;
import org.son.haruharu.common.exception.NotFoundException;
import org.son.haruharu.common.exception.UnauthorizedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.DisconnectedClientHelper;

/** 어드민 서버 공통 예외 처리. 유저 서버 GlobalExceptionHandler와 동일한 ApiResponse 계약. */
@Slf4j
@RestControllerAdvice
public class AdminExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    /** 칸별 메시지를 error.fields 로 내린다(#457 편성 검증). BadRequestException 보다 구체적이라 먼저 잡힌다. */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleFieldValidation(FieldValidationException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage(), e.getFieldErrors())));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(new ApiErrorDetail(e.getCode(), e.getMessage())));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
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

    /** multipart 상한(미션 사진 10MB) 초과. 기본 처리로 두면 500 으로 떨어진다. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ApiResponse.error(new ApiErrorDetail("UPLOAD_TOO_LARGE", "10MB 이하 파일을 선택해 주세요.")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiErrorDetail("INVALID_REQUEST_BODY", "잘못된 요청 바디입니다.")));
    }

    // 유니크 제약 위반(예: 상품 storeProductId 동시 생성 레이스)을 500이 아닌 409로 매핑.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(new ApiErrorDetail("DATA_INTEGRITY_VIOLATION", "중복되거나 제약을 위반한 데이터입니다.")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(new ApiErrorDetail("NOT_FOUND", "요청한 경로를 찾을 수 없습니다.")));
    }

    /** 유저 서버와 동일 방침(#259) — 클라 연결 종료는 서버 버그가 아니므로 ERROR·스택으로 남기지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
            log.debug("클라이언트 연결 종료");
        } else {
            log.error("Unexpected admin error", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(new ApiErrorDetail("INTERNAL_ERROR", "서버 오류가 발생했습니다.")));
    }
}

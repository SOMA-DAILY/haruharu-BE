package org.son.haruharu.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 실패 응답의 error 객체. fields 는 칸별 검증 메시지(VALIDATION_ERROR)일 때만 채운다 — 그 외에는 직렬화에서 빠진다(#455).
 */
public record ApiErrorDetail(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ApiFieldError> fields) {

    public ApiErrorDetail(String code, String message) {
        this(code, message, null);
    }
}

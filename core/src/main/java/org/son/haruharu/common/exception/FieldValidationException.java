package org.son.haruharu.common.exception;

import org.son.haruharu.common.ApiFieldError;

import java.util.List;

/**
 * 칸별 메시지를 함께 내리는 400(VALIDATION_ERROR, #455). message 는 첫 칸의 메시지다.
 * 핸들러가 fieldErrors 를 error.fields 로 직렬화한다.
 */
public abstract class FieldValidationException extends BadRequestException {

    public static final String CODE = "VALIDATION_ERROR";

    private final List<ApiFieldError> fieldErrors;

    protected FieldValidationException(List<ApiFieldError> fieldErrors) {
        super(CODE, fieldErrors.isEmpty() ? "잘못된 요청입니다." : fieldErrors.get(0).message());
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<ApiFieldError> getFieldErrors() {
        return fieldErrors;
    }
}

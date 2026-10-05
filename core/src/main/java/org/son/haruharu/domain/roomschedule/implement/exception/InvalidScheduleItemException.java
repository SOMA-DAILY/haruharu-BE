package org.son.haruharu.domain.roomschedule.implement.exception;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.common.exception.FieldValidationException;

import java.util.List;

/** 편성 행 검증 실패(#457). 400 VALIDATION_ERROR + error.fields(snake_case 칸 이름). */
public class InvalidScheduleItemException extends FieldValidationException {
    public InvalidScheduleItemException(List<ApiFieldError> fieldErrors) {
        super(fieldErrors);
    }
}

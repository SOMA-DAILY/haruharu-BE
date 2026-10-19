package org.son.haruharu.domain.cohort.implement.exception;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.common.exception.FieldValidationException;

import java.util.List;

/** 신청서·설문의 칸 오류(400 VALIDATION_ERROR). */
public class InvalidCohortApplicationException extends FieldValidationException {
    public InvalidCohortApplicationException(List<ApiFieldError> fieldErrors) {
        super(fieldErrors);
    }
}

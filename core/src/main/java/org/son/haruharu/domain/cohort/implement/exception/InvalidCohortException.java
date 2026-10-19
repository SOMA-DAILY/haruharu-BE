package org.son.haruharu.domain.cohort.implement.exception;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.common.exception.FieldValidationException;

import java.util.List;

public class InvalidCohortException extends FieldValidationException {
    public InvalidCohortException(String field, String message) {
        super(List.of(new ApiFieldError(field, message)));
    }

    public InvalidCohortException(List<ApiFieldError> fieldErrors) {
        super(fieldErrors);
    }
}

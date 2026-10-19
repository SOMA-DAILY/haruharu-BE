package org.son.haruharu.domain.cohort.implement.exception;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.common.exception.FieldValidationException;

import java.util.List;

public class InvalidRecruitTrackException extends FieldValidationException {
    public InvalidRecruitTrackException(List<ApiFieldError> fieldErrors) {
        super(fieldErrors);
    }
}

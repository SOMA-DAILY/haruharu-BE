package org.son.haruharu.domain.mission.common.implement.exception;

import java.util.List;
import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.common.exception.FieldValidationException;

public class InvalidMissionException extends FieldValidationException {
    public InvalidMissionException(String field, String message) {
        super(List.of(new ApiFieldError(field, message)));
    }

    public InvalidMissionException(List<ApiFieldError> fields) {
        super(fields);
    }
}

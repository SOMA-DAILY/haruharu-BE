package org.son.haruharu.domain.profile.implement.exception;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.common.exception.FieldValidationException;

import java.util.List;

/** 방 온보딩 프로필 칸별 검증 실패(#455). 400 VALIDATION_ERROR + error.fields. */
public class InvalidRoomProfileException extends FieldValidationException {

    public InvalidRoomProfileException(List<ApiFieldError> fieldErrors) {
        super(fieldErrors);
    }
}

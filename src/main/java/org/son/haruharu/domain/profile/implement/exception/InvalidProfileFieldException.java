package org.son.haruharu.domain.profile.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidProfileFieldException extends BadRequestException {

    public InvalidProfileFieldException() {
        super("PROFILE_INVALID_FIELD", "존재하지 않는 프로필 항목입니다.");
    }
}

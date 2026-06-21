package org.son.haruharu.domain.user.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class UserAlreadyVerifiedException extends ConflictException {

    public UserAlreadyVerifiedException() {
        super("USER_ALREADY_VERIFIED", "이미 본인인증이 완료된 사용자입니다.");
    }
}

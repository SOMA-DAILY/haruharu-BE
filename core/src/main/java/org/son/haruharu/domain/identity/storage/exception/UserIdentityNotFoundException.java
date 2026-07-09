package org.son.haruharu.domain.identity.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class UserIdentityNotFoundException extends NotFoundException {

    public UserIdentityNotFoundException() {
        super("USER_IDENTITY_NOT_FOUND", "본인인증 정보를 찾을 수 없습니다.");
    }
}

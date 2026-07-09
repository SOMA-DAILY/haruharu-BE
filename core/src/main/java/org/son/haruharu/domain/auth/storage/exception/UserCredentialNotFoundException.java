package org.son.haruharu.domain.auth.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class UserCredentialNotFoundException extends NotFoundException {

    public UserCredentialNotFoundException() {
        super("USER_CREDENTIAL_NOT_FOUND", "인증 정보를 찾을 수 없습니다.");
    }
}

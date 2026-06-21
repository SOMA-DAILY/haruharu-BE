package org.son.haruharu.domain.auth.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class AlreadyExistsCredentialException extends ConflictException {

    public AlreadyExistsCredentialException() {
        super("AUTH_409_ALREADY_EXISTS", "이미 가입된 소셜 계정 정보가 존재합니다.");
    }
}

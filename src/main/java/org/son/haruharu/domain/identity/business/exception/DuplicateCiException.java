package org.son.haruharu.domain.identity.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class DuplicateCiException extends ConflictException {
    public DuplicateCiException() {
        super("IDENTITY_ALREADY_LINKED", "이미 다른 계정에 연결된 본인인증 정보입니다.");
    }
}

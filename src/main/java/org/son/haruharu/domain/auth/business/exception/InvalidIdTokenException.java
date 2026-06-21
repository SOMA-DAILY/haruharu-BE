package org.son.haruharu.domain.auth.business.exception;

import org.son.haruharu.common.exception.UnauthorizedException;

public class InvalidIdTokenException extends UnauthorizedException {

    public InvalidIdTokenException() {
        super("AUTH_401_INVALID_ID_TOKEN", "유효하지 않은 idToken입니다.");
    }
}

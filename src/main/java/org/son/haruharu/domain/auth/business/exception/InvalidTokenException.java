package org.son.haruharu.domain.auth.business.exception;

import org.son.haruharu.common.exception.UnauthorizedException;

public class InvalidTokenException extends UnauthorizedException {

    public InvalidTokenException() {
        super("AUTH_INVALID_TOKEN", "유효하지 않은 토큰입니다.");
    }
}

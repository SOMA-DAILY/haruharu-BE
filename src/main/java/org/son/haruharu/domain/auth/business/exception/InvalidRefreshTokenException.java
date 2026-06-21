package org.son.haruharu.domain.auth.business.exception;

import org.son.haruharu.common.exception.UnauthorizedException;

public class InvalidRefreshTokenException extends UnauthorizedException {

    public InvalidRefreshTokenException() {
        super("AUTH_401_INVALID_REFRESH", "유효하지 않거나 만료된 리프레시 토큰입니다.");
    }
}

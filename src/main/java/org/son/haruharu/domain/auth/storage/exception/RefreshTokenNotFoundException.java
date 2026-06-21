package org.son.haruharu.domain.auth.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class RefreshTokenNotFoundException extends NotFoundException {

    public RefreshTokenNotFoundException() {
        super("REFRESH_TOKEN_NOT_FOUND", "유효하지 않은 토큰입니다.");
    }
}

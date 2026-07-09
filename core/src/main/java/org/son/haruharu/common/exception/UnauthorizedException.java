package org.son.haruharu.common.exception;

public abstract class UnauthorizedException extends BusinessException {

    protected UnauthorizedException(String code, String message) {
        super(code, message);
    }
}

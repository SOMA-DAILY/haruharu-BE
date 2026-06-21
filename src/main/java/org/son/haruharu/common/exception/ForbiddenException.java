package org.son.haruharu.common.exception;

public abstract class ForbiddenException extends BusinessException {

    protected ForbiddenException(String code, String message) {
        super(code, message);
    }
}

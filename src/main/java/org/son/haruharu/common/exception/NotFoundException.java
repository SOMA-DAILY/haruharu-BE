package org.son.haruharu.common.exception;

public abstract class NotFoundException extends BusinessException {

    protected NotFoundException(String code, String message) {
        super(code, message);
    }
}

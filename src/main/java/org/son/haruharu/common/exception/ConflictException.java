package org.son.haruharu.common.exception;

public abstract class ConflictException extends BusinessException {

    protected ConflictException(String code, String message) {
        super(code, message);
    }
}

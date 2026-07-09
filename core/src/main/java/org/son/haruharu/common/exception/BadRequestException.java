package org.son.haruharu.common.exception;

public abstract class BadRequestException extends BusinessException {

    protected BadRequestException(String code, String message) {
        super(code, message);
    }
}

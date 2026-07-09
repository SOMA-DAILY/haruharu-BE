package org.son.haruharu.common.exception;

public abstract class BadGatewayException extends BusinessException {

    protected BadGatewayException(String code, String message) {
        super(code, message);
    }
}

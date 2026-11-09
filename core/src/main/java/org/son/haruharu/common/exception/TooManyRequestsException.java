package org.son.haruharu.common.exception;

/** 짧은 시간에 너무 많은 요청(429). 잠시 뒤 다시 시도할 수 있다. */
public abstract class TooManyRequestsException extends BusinessException {

    protected TooManyRequestsException(String code, String message) {
        super(code, message);
    }
}

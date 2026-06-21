package org.son.haruharu.common.exception;

public abstract class UnauthorizedException extends RuntimeException {

    private final String code;

    protected UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

package org.son.haruharu.common.exception;

public abstract class BadRequestException extends RuntimeException {

    private final String code;

    protected BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

package org.son.haruharu.common.exception;

public abstract class ConflictException extends RuntimeException {

    private final String code;

    protected ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

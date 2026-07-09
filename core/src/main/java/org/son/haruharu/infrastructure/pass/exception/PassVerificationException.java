package org.son.haruharu.infrastructure.pass.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class PassVerificationException extends BadRequestException {
    public PassVerificationException() {
        super("PASS_VERIFICATION_FAILED", "본인인증에 실패했습니다. 다시 시도해주세요.");
    }
}

package org.son.haruharu.domain.auth.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class ReRegistrationBlockedException extends ForbiddenException {
    public ReRegistrationBlockedException() {
        super("AUTH_REJOIN_BLOCKED", "탈퇴 후 30일간 재가입이 제한됩니다.");
    }
}

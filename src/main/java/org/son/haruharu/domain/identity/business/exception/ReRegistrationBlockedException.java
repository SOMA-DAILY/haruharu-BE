package org.son.haruharu.domain.identity.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class ReRegistrationBlockedException extends ForbiddenException {
    public ReRegistrationBlockedException() {
        super("IDENTITY_REJOIN_BLOCKED", "탈퇴 후 30일간 재가입이 제한됩니다.");
    }
}

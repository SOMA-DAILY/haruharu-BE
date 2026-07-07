package org.son.haruharu.domain.agreement.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class RequiredAgreementMissingException extends BadRequestException {
    public RequiredAgreementMissingException() {
        super("REQUIRED_AGREEMENT_MISSING", "필수 약관에 모두 동의해야 합니다.");
    }
}

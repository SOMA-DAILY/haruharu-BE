package org.son.haruharu.admin.domain.audit.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidAuditPeriodException extends BadRequestException {
    public InvalidAuditPeriodException() {
        super("AUDIT_INVALID_PERIOD", "점검 대상 월은 YYYY-MM 형식이어야 합니다.");
    }
}

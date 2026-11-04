package org.son.haruharu.admin.domain.audit.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidAuditRangeException extends BadRequestException {
    public InvalidAuditRangeException() {
        super("AUDIT_INVALID_RANGE", "조회 시작일은 종료일보다 늦을 수 없습니다.");
    }
}

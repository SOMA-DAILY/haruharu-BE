package org.son.haruharu.admin.domain.report.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class AdminReportAlreadyResolvedException extends ConflictException {
    public AdminReportAlreadyResolvedException() {
        super("REPORT_ALREADY_RESOLVED", "이미 처리된 신고입니다.");
    }
}

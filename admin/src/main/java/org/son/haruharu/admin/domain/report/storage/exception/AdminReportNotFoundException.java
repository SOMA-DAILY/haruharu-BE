package org.son.haruharu.admin.domain.report.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class AdminReportNotFoundException extends NotFoundException {
    public AdminReportNotFoundException() {
        super("ADMIN_REPORT_NOT_FOUND", "존재하지 않는 신고입니다.");
    }
}

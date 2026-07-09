package org.son.haruharu.domain.report.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class ReportAlreadyExistsException extends ConflictException {

    public ReportAlreadyExistsException() {
        super("REPORT_ALREADY_EXISTS", "이미 신고한 사용자입니다.");
    }
}

package org.son.haruharu.domain.report.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class CannotReportSelfException extends BadRequestException {

    public CannotReportSelfException() {
        super("CANNOT_REPORT_SELF", "자기 자신은 신고할 수 없습니다.");
    }
}

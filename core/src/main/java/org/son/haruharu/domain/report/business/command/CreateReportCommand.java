package org.son.haruharu.domain.report.business.command;

import org.son.haruharu.domain.report.implement.ReportReason;

/** 신고 접수 명령. detail은 선택 입력(null 허용). */
public record CreateReportCommand(
        Long reportedUserId,
        ReportReason reason,
        String detail
) {
}

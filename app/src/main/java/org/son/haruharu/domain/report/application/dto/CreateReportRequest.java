package org.son.haruharu.domain.report.application.dto;

import jakarta.validation.constraints.NotNull;
import org.son.haruharu.domain.report.business.command.CreateReportCommand;
import org.son.haruharu.domain.report.implement.ReportReason;

/** 유저 신고 요청. 전역 SNAKE_CASE 역직렬화(reported_user_id 등). detail은 선택. */
public record CreateReportRequest(
        @NotNull Long reportedUserId,
        @NotNull ReportReason reason,
        String detail
) {
    public CreateReportCommand toCommand() {
        return new CreateReportCommand(reportedUserId, reason, detail);
    }
}

package org.son.haruharu.domain.question.application.dto;

import jakarta.validation.constraints.*;

import org.son.haruharu.domain.question.business.command.ReportQuestionCommand;
import org.son.haruharu.domain.report.implement.ReportReason;

public record ReportQuestionRequest(@NotNull ReportReason reason, @Size(max = 500) String detail) {
    public ReportQuestionCommand toCommand() {
        return new ReportQuestionCommand(reason, detail);
    }
}

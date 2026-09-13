package org.son.haruharu.domain.question.business.command;

import org.son.haruharu.domain.report.implement.ReportReason;

public record ReportQuestionCommand(ReportReason reason, String detail) {}

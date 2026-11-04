package org.son.haruharu.admin.domain.audit.business.command;

public record CreateAuditReviewCommand(Long adminId, String period, String note) {
}

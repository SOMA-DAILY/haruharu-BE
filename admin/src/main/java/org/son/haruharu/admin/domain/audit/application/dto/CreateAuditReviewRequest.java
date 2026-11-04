package org.son.haruharu.admin.domain.audit.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.son.haruharu.admin.domain.audit.business.command.CreateAuditReviewCommand;

public record CreateAuditReviewRequest(
        @Schema(description = "점검 대상 월", example = "2026-11") @NotBlank String period,
        @Schema(description = "점검 메모") @Size(max = 500) String note
) {
    public CreateAuditReviewCommand toCommand(Long adminId) {
        return new CreateAuditReviewCommand(adminId, period, note);
    }
}

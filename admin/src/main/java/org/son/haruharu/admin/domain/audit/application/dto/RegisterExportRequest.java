package org.son.haruharu.admin.domain.audit.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.son.haruharu.admin.domain.audit.business.command.RegisterExportCommand;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditTargetType;

public record RegisterExportRequest(
        @Schema(description = "반출 대상 종류", example = "ROOM") @NotNull AdminAuditTargetType targetType,
        @Schema(description = "반출 대상 ID", example = "107") @NotNull Long targetId,
        @Schema(description = "반출 범위(화면이 정한 문자열)", example = "videos+chats") @Size(max = 200) String scope,
        @Schema(description = "반출 사유(5~500자)") @NotBlank @Size(min = 5, max = 500) String reason
) {
    public RegisterExportCommand toCommand(Long adminId, String ip, String userAgent) {
        return new RegisterExportCommand(adminId, targetType, targetId, scope, reason, ip, userAgent);
    }
}

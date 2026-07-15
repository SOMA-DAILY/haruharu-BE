package org.son.haruharu.admin.domain.account.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.admin.domain.account.business.command.UpdateAdminAccountCommand;
import org.son.haruharu.admin.domain.account.implement.AdminAccountStatus;
import org.son.haruharu.admin.domain.account.implement.AdminRole;

/** 부분 수정 — 보낸 필드만 갱신. email·password는 수정 불가. */
public record UpdateAdminAccountRequest(
        @Schema(description = "역할", example = "OPERATOR") AdminRole role,
        @Schema(description = "상태(ACTIVE/INACTIVE)", example = "INACTIVE") AdminAccountStatus status
) {
    public UpdateAdminAccountCommand toCommand() {
        return new UpdateAdminAccountCommand(role, status);
    }
}

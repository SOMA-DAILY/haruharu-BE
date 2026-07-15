package org.son.haruharu.admin.domain.account.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.son.haruharu.admin.domain.account.business.command.CreateAdminAccountCommand;
import org.son.haruharu.admin.domain.account.implement.AdminRole;

public record CreateAdminAccountRequest(
        @Schema(description = "이메일", example = "ops@haruharu.co.kr")
        @NotBlank @Email String email,
        @Schema(description = "초기 비밀번호(8자 이상)", example = "Str0ngP@ss")
        @NotBlank @Size(min = 8) String password,
        @Schema(description = "이름", example = "운영자")
        @NotBlank String name,
        @Schema(description = "역할", example = "OPERATOR")
        @NotNull AdminRole role
) {
    public CreateAdminAccountCommand toCommand() {
        return new CreateAdminAccountCommand(email, password, name, role);
    }
}

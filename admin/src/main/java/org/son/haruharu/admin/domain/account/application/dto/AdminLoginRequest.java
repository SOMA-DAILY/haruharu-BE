package org.son.haruharu.admin.domain.account.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.son.haruharu.admin.domain.account.business.command.AdminLoginCommand;

public record AdminLoginRequest(
        @Schema(description = "관리자 이메일", example = "admin@haruharu.app")
        @NotBlank @Email String email,
        @Schema(description = "비밀번호", example = "P@ssw0rd!")
        @NotBlank String password
) {
    public AdminLoginCommand toCommand() {
        return new AdminLoginCommand(email, password);
    }
}

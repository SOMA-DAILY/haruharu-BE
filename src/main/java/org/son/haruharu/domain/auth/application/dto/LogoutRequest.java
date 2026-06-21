package org.son.haruharu.domain.auth.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.son.haruharu.domain.auth.business.command.LogoutCommand;

public record LogoutRequest(
    @JsonProperty("refresh_token") @NotBlank String refreshToken
) {
    public LogoutCommand toCommand() {
        return new LogoutCommand(refreshToken);
    }
}

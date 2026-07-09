package org.son.haruharu.domain.auth.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.son.haruharu.domain.auth.business.command.TokenRefreshCommand;

public record TokenRefreshRequest(
    @JsonProperty("refresh_token") @NotBlank String refreshToken
) {
    public TokenRefreshCommand toCommand() {
        return new TokenRefreshCommand(refreshToken);
    }
}

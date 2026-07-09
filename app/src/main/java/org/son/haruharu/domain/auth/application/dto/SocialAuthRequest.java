package org.son.haruharu.domain.auth.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.son.haruharu.domain.auth.business.command.SocialAuthCommand;
import org.son.haruharu.domain.auth.implement.ProviderType;

public record SocialAuthRequest(
    @JsonProperty("provider_type") @NotNull ProviderType providerType,
    @JsonProperty("id_token") @NotBlank String idToken
) {
    public SocialAuthCommand toCommand() {
        return new SocialAuthCommand(providerType, idToken);
    }
}

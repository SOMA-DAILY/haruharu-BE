package org.son.haruharu.domain.identity.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;

public record IdentityVerifyRequest(
        @NotBlank @JsonProperty("pass_token") String identityVerificationId) {

    public VerifyIdentityCommand toCommand() {
        return new VerifyIdentityCommand(identityVerificationId);
    }
}

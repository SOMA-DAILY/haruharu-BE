package org.son.haruharu.domain.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;

public record IdentityVerifyRequest(@NotBlank String passToken) {

    public VerifyIdentityCommand toCommand() {
        return new VerifyIdentityCommand(passToken);
    }
}

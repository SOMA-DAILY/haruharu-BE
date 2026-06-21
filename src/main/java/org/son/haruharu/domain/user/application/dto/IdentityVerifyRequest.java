package org.son.haruharu.domain.user.application.dto;

import jakarta.validation.constraints.NotBlank;
import org.son.haruharu.domain.user.business.command.VerifyIdentityCommand;

public record IdentityVerifyRequest(@NotBlank String passToken) {

    public VerifyIdentityCommand toCommand() {
        return new VerifyIdentityCommand(passToken);
    }
}

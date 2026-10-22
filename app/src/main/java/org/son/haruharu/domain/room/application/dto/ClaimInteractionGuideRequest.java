package org.son.haruharu.domain.room.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.son.haruharu.domain.room.business.command.ClaimInteractionGuideCommand;
import org.son.haruharu.domain.room.implement.InteractionGuideKind;

public record ClaimInteractionGuideRequest(@NotNull InteractionGuideKind kind,
        @NotNull @Positive Long videoPostId, @NotBlank @Size(max = 100) String viewerSessionId) {
    public ClaimInteractionGuideCommand toCommand() {
        return new ClaimInteractionGuideCommand(kind, videoPostId, viewerSessionId);
    }
}

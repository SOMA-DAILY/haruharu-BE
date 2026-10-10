package org.son.haruharu.domain.video.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.son.haruharu.domain.video.business.command.VideoCaptionUpdateCommand;

public record VideoCaptionUpdateRequest(@NotNull @Size(max = 100) String caption) {
    public VideoCaptionUpdateCommand toCommand() {
        return new VideoCaptionUpdateCommand(caption);
    }
}

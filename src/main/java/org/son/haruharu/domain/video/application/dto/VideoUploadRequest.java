package org.son.haruharu.domain.video.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.son.haruharu.domain.video.business.command.VideoUploadCommand;

public record VideoUploadRequest(
        @NotNull @Positive Integer slotIndex,
        @NotBlank String videoUrl,
        String thumbnailUrl,
        @Size(max = 20) String caption
) {
    public VideoUploadCommand toCommand() {
        return new VideoUploadCommand(slotIndex, videoUrl, thumbnailUrl, caption);
    }
}

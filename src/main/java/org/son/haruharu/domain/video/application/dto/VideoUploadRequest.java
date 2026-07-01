package org.son.haruharu.domain.video.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.son.haruharu.domain.video.business.command.VideoUploadCommand;

import java.util.UUID;

public record VideoUploadRequest(
        @NotNull @Positive Integer slotIndex,
        UUID missionId,
        @NotBlank String videoUrl,
        String thumbnailUrl,
        @Size(max = 20) String caption
) {
    public VideoUploadCommand toCommand() {
        return new VideoUploadCommand(slotIndex, missionId, videoUrl, thumbnailUrl, caption);
    }
}

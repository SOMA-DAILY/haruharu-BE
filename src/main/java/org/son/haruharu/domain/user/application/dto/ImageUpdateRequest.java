package org.son.haruharu.domain.user.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.son.haruharu.domain.user.business.command.UpdateImagesCommand;

import java.util.List;

public record ImageUpdateRequest(
        @NotNull @Size(max = 10) List<String> imageUrls) {

    public UpdateImagesCommand toCommand() {
        return new UpdateImagesCommand(imageUrls);
    }
}

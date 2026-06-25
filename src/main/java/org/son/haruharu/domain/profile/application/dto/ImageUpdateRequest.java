package org.son.haruharu.domain.profile.application.dto;

import jakarta.validation.constraints.NotNull;
import org.son.haruharu.domain.profile.business.command.UpdateImagesCommand;

import java.util.List;

// 최소/최대 개수는 ProfileService에서 IMAGE_* 도메인 예외로 검증한다(명세 에러코드 일치).
public record ImageUpdateRequest(@NotNull List<String> imageUrls) {

    public UpdateImagesCommand toCommand() {
        return new UpdateImagesCommand(imageUrls);
    }
}

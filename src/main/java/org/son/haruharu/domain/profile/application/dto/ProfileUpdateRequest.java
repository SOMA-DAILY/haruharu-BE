package org.son.haruharu.domain.profile.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;

public record ProfileUpdateRequest(
        String location,
        String affiliation,
        Integer height,
        @Size(max = 4) String mbti,
        @NotBlank @Size(max = 20) String nickname,
        DrinkingStatus drinkingStatus,
        SmokingStatus smokingStatus
) {
    public UpdateProfileCommand toCommand() {
        return new UpdateProfileCommand(
            location, affiliation,
            height, mbti, nickname,
            drinkingStatus, smokingStatus
        );
    }
}

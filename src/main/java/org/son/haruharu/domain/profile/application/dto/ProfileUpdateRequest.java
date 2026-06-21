package org.son.haruharu.domain.profile.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;

import java.time.LocalDate;

public record ProfileUpdateRequest(
        String gender,
        LocalDate birthday,
        String location,
        String affiliation,
        Integer height,
        @Size(max = 4) String mbti,
        @NotBlank @Size(max = 20) String nickname,
        String drinkingStatus,
        String smokingStatus,
        String religion,
        String atmosphereKeywords
) {
    public UpdateProfileCommand toCommand() {
        return new UpdateProfileCommand(
            gender, birthday, location, affiliation,
            height, mbti, nickname,
            drinkingStatus, smokingStatus, religion, atmosphereKeywords
        );
    }
}

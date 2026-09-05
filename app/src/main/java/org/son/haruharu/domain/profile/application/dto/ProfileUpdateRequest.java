package org.son.haruharu.domain.profile.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.SmokingStatus;

// gender 는 온보딩에선 필수지만 편집(마이페이지)에선 미전송 허용이라 요청 검증에서 @NotNull 을 두지 않는다.
// 필수 강제는 온보딩 완료 게이트(ProfileService.hasCompletedProfile)가 담당한다. applyTo 는 null 이면 기존 성별을 보존한다.
public record ProfileUpdateRequest(
        String location,
        String affiliation,
        Integer height,
        @Size(max = 4) String mbti,
        @NotBlank @Size(max = 20) String nickname,
        DrinkingStatus drinkingStatus,
        SmokingStatus smokingStatus,
        Gender gender
) {
    public UpdateProfileCommand toCommand() {
        return new UpdateProfileCommand(
            location, affiliation,
            height, mbti, nickname,
            drinkingStatus, smokingStatus, gender
        );
    }
}

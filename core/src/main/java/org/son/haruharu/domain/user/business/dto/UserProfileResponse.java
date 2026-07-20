package org.son.haruharu.domain.user.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;

import java.util.List;

public record UserProfileResponse(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("user_status") String userStatus,
        @JsonProperty("demo_mode") boolean demoMode,
        ProfileSection profile,
        List<ProfileResponse.ImageInfo> images
) {
    public record ProfileSection(
            String nickname,
            String gender,
            Integer age,
            String location,
            String affiliation,
            Integer height,
            String mbti,
            @JsonProperty("drinking_status") DrinkingStatus drinkingStatus,
            @JsonProperty("smoking_status") SmokingStatus smokingStatus
    ) {}

    /** gender·age는 프로필이 아니라 본인인증(identity)에서 수급한다. demoMode는 users 테이블 플래그(#264). */
    public static UserProfileResponse from(Long userId, ProfileResponse profile,
                                           String gender, Integer age, String userStatus, boolean demoMode) {
        return new UserProfileResponse(
            userId,
            userStatus,
            demoMode,
            new ProfileSection(
                profile.nickname(), gender, age,
                profile.location(), profile.affiliation(), profile.height(),
                profile.mbti(), profile.drinkingStatus(), profile.smokingStatus()
            ),
            profile.images()
        );
    }
}

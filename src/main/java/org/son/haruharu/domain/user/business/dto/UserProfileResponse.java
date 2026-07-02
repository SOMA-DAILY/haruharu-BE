package org.son.haruharu.domain.user.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;

import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("user_status") String userStatus,
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
            @JsonProperty("drinking_status") String drinkingStatus,
            @JsonProperty("smoking_status") String smokingStatus
    ) {}

    /** gender·age는 프로필이 아니라 본인인증(identity)에서 수급한다. */
    public static UserProfileResponse from(UUID userId, ProfileResponse profile,
                                           String gender, Integer age, String userStatus) {
        return new UserProfileResponse(
            userId,
            userStatus,
            new ProfileSection(
                profile.nickname(), gender, age,
                profile.location(), profile.affiliation(), profile.height(),
                profile.mbti(), profile.drinkingStatus(), profile.smokingStatus()
            ),
            profile.images()
        );
    }
}

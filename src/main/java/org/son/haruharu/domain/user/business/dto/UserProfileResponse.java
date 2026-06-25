package org.son.haruharu.domain.user.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;

import java.util.List;

public record UserProfileResponse(
        String nickname,
        String gender,
        Integer age,
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String drinkingStatus,
        String smokingStatus,
        String religion,
        List<String> imageUrls,
        @JsonProperty("is_identity_verified") boolean isIdentityVerified
) {
    public static UserProfileResponse from(ProfileResponse profile, boolean verified) {
        return new UserProfileResponse(
            profile.nickname(), profile.gender(), profile.age(),
            profile.location(), profile.affiliation(), profile.height(),
            profile.mbti(), profile.drinkingStatus(), profile.smokingStatus(),
            profile.religion(), profile.imageUrls(),
            verified
        );
    }
}

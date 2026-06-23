package org.son.haruharu.domain.user.business.dto;

import org.son.haruharu.domain.profile.business.dto.ProfileResponse;

import java.time.LocalDate;
import java.util.List;

public record UserProfileResponse(
        String nickname,
        String gender,
        LocalDate birthday,
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String drinkingStatus,
        String smokingStatus,
        String religion,
        String atmosphereKeywords,
        List<String> imageUrls,
        boolean isIdentityVerified
) {
    public static UserProfileResponse from(ProfileResponse profile, boolean verified) {
        return new UserProfileResponse(
            profile.nickname(), profile.gender(), profile.birthday(),
            profile.location(), profile.affiliation(), profile.height(),
            profile.mbti(), profile.drinkingStatus(), profile.smokingStatus(),
            profile.religion(), profile.atmosphereKeywords(),
            profile.imageUrls(),
            verified
        );
    }
}

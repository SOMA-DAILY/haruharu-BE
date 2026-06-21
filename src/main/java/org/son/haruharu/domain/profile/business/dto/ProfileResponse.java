package org.son.haruharu.domain.profile.business.dto;

import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserProfile;

import java.time.LocalDate;
import java.util.List;

public record ProfileResponse(
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
        List<String> imageUrls
) {
    public static ProfileResponse from(UserProfile profile, List<UserImage> images) {
        return new ProfileResponse(
            profile.nickname(), profile.gender(), profile.birthday(),
            profile.location(), profile.affiliation(), profile.height(), profile.mbti(),
            profile.drinkingStatus(), profile.smokingStatus(), profile.religion(),
            profile.atmosphereKeywords(),
            images.stream().map(UserImage::imageUrl).toList()
        );
    }
}

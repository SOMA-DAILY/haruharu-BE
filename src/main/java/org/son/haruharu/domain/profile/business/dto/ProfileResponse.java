package org.son.haruharu.domain.profile.business.dto;

import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserProfile;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

public record ProfileResponse(
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
        List<String> imageUrls
) {
    public static ProfileResponse from(UserProfile profile, List<UserImage> images) {
        Integer age = profile.birthday() == null ? null
            : Period.between(profile.birthday(), LocalDate.now()).getYears();
        return new ProfileResponse(
            profile.nickname(), profile.gender(), age,
            profile.location(), profile.affiliation(), profile.height(), profile.mbti(),
            profile.drinkingStatus(), profile.smokingStatus(), profile.religion(),
            images.stream().map(UserImage::imageUrl).toList()
        );
    }
}

package org.son.haruharu.domain.profile.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserProfile;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

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
        List<ImageInfo> images
) {
    public record ImageInfo(
            UUID id,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("sorted_order") int sortedOrder
    ) {}

    public static ProfileResponse from(UserProfile profile, List<UserImage> images) {
        Integer age = profile.birthday() == null ? null
            : Period.between(profile.birthday(), LocalDate.now()).getYears();
        return new ProfileResponse(
            profile.nickname(), profile.gender(), age,
            profile.location(), profile.affiliation(), profile.height(), profile.mbti(),
            profile.drinkingStatus(), profile.smokingStatus(), profile.religion(),
            images.stream().map(i -> new ImageInfo(i.id(), i.imageUrl(), i.sortedOrder())).toList()
        );
    }
}

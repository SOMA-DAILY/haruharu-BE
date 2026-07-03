package org.son.haruharu.domain.profile.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserProfile;

import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        String nickname,
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String drinkingStatus,
        String smokingStatus,
        List<ImageInfo> images
) {
    public record ImageInfo(
            UUID id,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("sorted_order") int sortedOrder
    ) {}

    public static ProfileResponse from(UserProfile profile, List<UserImage> images) {
        return new ProfileResponse(
            profile.nickname(),
            profile.location(), profile.affiliation(), profile.height(), profile.mbti(),
            profile.drinkingStatus(), profile.smokingStatus(),
            images.stream().map(i -> new ImageInfo(i.id(), i.imageUrl(), i.sortedOrder())).toList()
        );
    }
}

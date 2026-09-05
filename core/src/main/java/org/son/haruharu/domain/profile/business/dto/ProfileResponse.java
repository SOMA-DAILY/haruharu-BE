package org.son.haruharu.domain.profile.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserProfile;

import java.util.List;
import java.util.function.UnaryOperator;

public record ProfileResponse(
        String nickname,
        String location,
        String affiliation,
        Integer height,
        String mbti,
        DrinkingStatus drinkingStatus,
        SmokingStatus smokingStatus,
        List<ImageInfo> images,
        Gender gender
) {
    public record ImageInfo(
            Long id,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("sorted_order") int sortedOrder
    ) {}

    /** imageUrlResolver: 저장된 objectUrl을 클라이언트가 접근 가능한 URL(presigned GET)로 변환한다. */
    public static ProfileResponse from(UserProfile profile, List<UserImage> images,
                                       UnaryOperator<String> imageUrlResolver) {
        return new ProfileResponse(
            profile.nickname(),
            profile.location(), profile.affiliation(), profile.height(), profile.mbti(),
            profile.drinkingStatus(), profile.smokingStatus(),
            images.stream()
                .map(i -> new ImageInfo(i.id(), imageUrlResolver.apply(i.imageUrl()), i.sortedOrder()))
                .toList(),
            profile.gender()
        );
    }
}

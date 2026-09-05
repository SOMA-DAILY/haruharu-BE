package org.son.haruharu.domain.user.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;

import java.util.List;

public record UserProfileResponse(
        @JsonProperty("user_id") Long userId,
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
            @JsonProperty("drinking_status") DrinkingStatus drinkingStatus,
            @JsonProperty("smoking_status") SmokingStatus smokingStatus
    ) {}

    /** age는 본인인증(identity)에서 수급한다. gender는 검증(PASS)이 있으면 그 값, 없으면 온보딩 자기신고 프로필 gender다(#392). */
    public static UserProfileResponse from(Long userId, ProfileResponse profile,
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

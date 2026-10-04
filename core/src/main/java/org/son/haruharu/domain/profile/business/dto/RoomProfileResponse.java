package org.son.haruharu.domain.profile.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.profile.implement.RoomProfile;

/** 방 온보딩 프로필(#455). 값이 없으면 null. age = 올해 − birth_year(만 나이 아님). */
public record RoomProfileResponse(
        @Schema(description = "출생연도", example = "2003", nullable = true) Integer birthYear,
        @Schema(description = "나이(올해 − 출생연도)", example = "23", nullable = true) Integer age,
        @Schema(description = "MBTI(16개 중 하나)", example = "ENFP", nullable = true) String mbti,
        @Schema(description = "학과", example = "전자공학과", nullable = true) String department,
        @Schema(description = "요즘 빠져 있는 것", example = "러닝", nullable = true) String intoNow,
        @Schema(description = "4칸이 다 있고 MBTI 가 유효하면 true", example = "true") boolean completed
) {
    public static RoomProfileResponse of(RoomProfile profile, int currentYear) {
        return new RoomProfileResponse(profile.birthYear(), profile.ageIn(currentYear), profile.mbti(),
                profile.department(), profile.intoNow(), profile.completed());
    }
}

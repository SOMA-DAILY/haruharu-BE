package org.son.haruharu.domain.profile.implement;

import java.time.LocalDate;
import java.util.UUID;

public record UserProfile(
        UUID id,
        UUID userId,
        String gender,
        LocalDate birthday,
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String nickname,
        String drinkingStatus,
        String smokingStatus,
        String religion,
        String atmosphereKeywords
) {
    /**
     * 아직 영속화되지 않은 빈 프로필. id는 null로 두어 저장 시 @GeneratedValue가 생성하게 한다.
     */
    public static UserProfile empty(UUID userId) {
        return new UserProfile(null, userId, null, null, null, null, null, null, null, null, null, null, null);
    }
}

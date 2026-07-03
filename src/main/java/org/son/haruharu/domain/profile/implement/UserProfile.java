package org.son.haruharu.domain.profile.implement;

import java.util.UUID;

public record UserProfile(
        UUID id,
        Long userId,
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String nickname,
        String drinkingStatus,
        String smokingStatus
) {
    /**
     * 아직 영속화되지 않은 빈 프로필. id는 null로 두어 저장 시 @GeneratedValue가 생성하게 한다.
     * 성별·생년월일은 본인인증(identity)이 단일 출처이므로 프로필에 두지 않는다.
     */
    public static UserProfile empty(Long userId) {
        return new UserProfile(null, userId, null, null, null, null, null, null, null);
    }
}

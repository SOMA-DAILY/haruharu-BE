package org.son.haruharu.domain.profile.business.command;

import org.son.haruharu.domain.profile.implement.UserProfile;

public record UpdateProfileCommand(
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String nickname,
        String drinkingStatus,
        String smokingStatus
) {
    public UserProfile applyTo(UserProfile existing) {
        return new UserProfile(
            existing.id(), existing.userId(),
            this.location(), this.affiliation(),
            this.height(), this.mbti(), this.nickname(),
            this.drinkingStatus(), this.smokingStatus()
        );
    }
}

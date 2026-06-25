package org.son.haruharu.domain.profile.business.command;

import org.son.haruharu.domain.profile.implement.UserProfile;

import java.time.LocalDate;

public record UpdateProfileCommand(
        String gender,
        LocalDate birthday,
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String nickname,
        String drinkingStatus,
        String smokingStatus,
        String religion
) {
    public UserProfile applyTo(UserProfile existing) {
        return new UserProfile(
            existing.id(), existing.userId(),
            this.gender(), this.birthday(), this.location(), this.affiliation(),
            this.height(), this.mbti(), this.nickname(),
            this.drinkingStatus(), this.smokingStatus(), this.religion()
        );
    }
}

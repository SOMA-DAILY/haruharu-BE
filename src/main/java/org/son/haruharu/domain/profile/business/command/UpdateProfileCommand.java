package org.son.haruharu.domain.profile.business.command;

import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserProfile;

public record UpdateProfileCommand(
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String nickname,
        DrinkingStatus drinkingStatus,
        SmokingStatus smokingStatus
) {
    public UserProfile applyTo(UserProfile existing) {
        // 음주·흡연은 온보딩에서만 받고 마이페이지 편집폼에는 없다. 미전송(null)이면 기존 값을 보존해
        // 편집 시 데이터가 소실되지 않게 한다. 나머지 필드는 편집폼에 모두 노출되므로 full-replace.
        return new UserProfile(
            existing.id(), existing.userId(),
            this.location(), this.affiliation(),
            this.height(), this.mbti(), this.nickname(),
            this.drinkingStatus() != null ? this.drinkingStatus() : existing.drinkingStatus(),
            this.smokingStatus() != null ? this.smokingStatus() : existing.smokingStatus()
        );
    }
}

package org.son.haruharu.domain.profile.business.command;

import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserProfile;

public record UpdateProfileCommand(
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String nickname,
        DrinkingStatus drinkingStatus,
        SmokingStatus smokingStatus,
        Gender gender
) {
    public UserProfile applyTo(UserProfile existing) {
        // 음주·흡연은 기존 클라이언트 호환 필드이며 현재 마이페이지 편집폼에는 없다. 미전송(null)이면 기존 값을 보존해
        // 편집 시 데이터가 소실되지 않게 한다. 나머지 필드는 편집폼에 모두 노출되므로 full-replace.
        // 성별(#392)도 미전송(null)이면 기존 값을 보존한다 — 온보딩에선 요청 검증(@NotNull)이 필수를 강제한다.
        return new UserProfile(
            existing.id(), existing.userId(),
            this.location(), this.affiliation(),
            this.height(), this.mbti(), this.nickname(),
            this.drinkingStatus() != null ? this.drinkingStatus() : existing.drinkingStatus(),
            this.smokingStatus() != null ? this.smokingStatus() : existing.smokingStatus(),
            this.gender() != null ? this.gender() : existing.gender(),
            // 방 온보딩 프로필(#455)은 이 API 가 다루지 않는다. 지금 앱 마이페이지 저장으로 지워지지 않게 기존 값을 보존한다.
            existing.birthYear(), existing.department(), existing.intoNow(),
            // 번호(#511)는 기수 신청에서만 받는다. 마이페이지 저장으로 지워지지 않게 보존한다.
            existing.phone()
        );
    }
}

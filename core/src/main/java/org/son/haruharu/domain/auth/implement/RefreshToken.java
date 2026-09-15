package org.son.haruharu.domain.auth.implement;

import java.time.Duration;
import java.time.LocalDateTime;

public record RefreshToken(
        Long id,
        Long userId,
        String tokenHash,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt
) {
    /**
     * 만료 판정은 주입 Clock이 아니라 시스템 시각 기준이다(#296, AuthService 클래스 주석 참조).
     * expiresAt 자체가 시스템 시각으로 계산돼 저장되므로 판정도 같은 기준을 써야 한다.
     * local·dev의 AdjustableClock(#180)으로 가상 시각을 옮겨도 토큰 만료는 실시간을 따른다 —
     * 시각을 앞당겨 방 진행을 테스트하는 동안 로그인이 풀리지 않게 하려는 의도다.
     * 그래서 만료 시나리오 테스트는 시각을 옮기는 대신 expiresAt을 과거로 세팅해 만든다.
     */
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** revoke 된 지 grace 이내인지. 병렬 재발급의 늦은 요청을 탈취 재사용과 구분하는 데 쓴다(#417). 시스템 시각 기준(isExpired 와 동일). */
    public boolean isRevokedWithin(Duration grace) {
        return isRevoked() && revokedAt.plus(grace).isAfter(LocalDateTime.now());
    }
}

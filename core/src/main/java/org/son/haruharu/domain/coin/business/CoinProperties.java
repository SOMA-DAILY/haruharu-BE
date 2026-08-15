package org.son.haruharu.domain.coin.business;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 코인 정책 설정. signupBonus(첫 가입 1회 보너스 구름)는 application.yaml `haruharu.coin.signup-bonus`로 관리한다.
 * enabled: 코인 경제(가입 보너스·코인 조회 API) 활성 여부. 2026-08-13 피벗으로 기본 false(비활성).
 *          코드는 보존하되 되살릴 때 true로 켠다. [[mvp-pivot-invite-code]]
 */
@ConfigurationProperties(prefix = "haruharu.coin")
public record CoinProperties(
        long signupBonus,
        @DefaultValue("false") boolean enabled
) {
}

package org.son.haruharu.domain.coin.business;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 코인 정책 설정. signupBonus(첫 가입 1회 보너스 구름)는 application.yaml `haruharu.coin.signup-bonus`로 관리한다. */
@ConfigurationProperties(prefix = "haruharu.coin")
public record CoinProperties(
        long signupBonus
) {
}

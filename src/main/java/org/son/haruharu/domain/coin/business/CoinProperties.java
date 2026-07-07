package org.son.haruharu.domain.coin.business;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 코인 정책 설정. signupBonus는 첫 가입 시 1회 지급하는 보너스 구름 개수(기본 5). */
@ConfigurationProperties(prefix = "haruharu.coin")
public record CoinProperties(
        @DefaultValue("5") long signupBonus
) {
}

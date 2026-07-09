package org.son.haruharu.domain.room.business;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 세션 미매칭 위로 구름 설정(#143). amount는 미매칭 멤버에게 지급하는 구름 개수(기본 5). yaml로 조정한다. */
@ConfigurationProperties(prefix = "haruharu.room.consolation-reward")
public record ConsolationRewardProperties(
        @DefaultValue("5") long amount
) {
}

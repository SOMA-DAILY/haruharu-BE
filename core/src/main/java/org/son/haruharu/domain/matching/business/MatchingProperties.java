package org.son.haruharu.domain.matching.business;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 매칭 정책 설정. enterCost는 신청 차감·미성사 환불·불발 통지 개수가 공유하는 단일 값. */
@ConfigurationProperties(prefix = "haruharu.matching")
public record MatchingProperties(
        long enterCost
) {
}

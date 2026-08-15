package org.son.haruharu.domain.matching.business;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 매칭 정책 설정. enterCost는 신청 차감·미성사 환불·불발 통지 개수가 공유하는 단일 값.
 * enabled: 자동매칭(22:00 배치·매칭 대기열 API) 활성 여부. 2026-08-13 피벗으로 기본 false(비활성).
 *          코드는 보존하되 되살릴 때 true로 켠다. [[mvp-pivot-invite-code]]
 */
@ConfigurationProperties(prefix = "haruharu.matching")
public record MatchingProperties(
        long enterCost,
        @DefaultValue("false") boolean enabled
) {
}

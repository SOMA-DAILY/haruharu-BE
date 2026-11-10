package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final Duration TRANSIENT = Duration.ofMinutes(30);
    private static final Duration PERMANENT = Duration.ofHours(24);

    private static RecommendationRequest closed(RecommendationReason reason, Duration ttl) {
        return RecommendationRequest.pending(1L, 2L, 3L, 4L, "v1", false, RecommendationRequest.NO_PERSONAL_HASH, NOW)
                .unavailable(reason, NOW, NOW.plus(ttl));
    }

    @Test
    @DisplayName("시간 초과·비용 한도·무효화·버전 없음은 다시 요청하면 새로 시도할 수 있다")
    void givenTransientReasons_whenRetryable_thenTrue() {
        // given // when // then
        assertThat(closed(RecommendationReason.TIMED_OUT, TRANSIENT).retryableUnavailable(PERMANENT)).isTrue();
        assertThat(closed(RecommendationReason.BUDGET_EXCEEDED, TRANSIENT).retryableUnavailable(PERMANENT)).isTrue();
        assertThat(closed(RecommendationReason.INVALIDATED, TRANSIENT).retryableUnavailable(PERMANENT)).isTrue();
        assertThat(closed(RecommendationReason.NOT_CONFIGURED, TRANSIENT).retryableUnavailable(PERMANENT)).isTrue();
    }

    @Test
    @DisplayName("분석·생성 실패는 일시 실패 유지 시간으로 닫혔을 때만 새로 시도한다")
    void givenFailureReasons_whenRetryable_thenOnlyTransientTtl() {
        // given // when // then
        assertThat(closed(RecommendationReason.ANALYSIS_FAILED, TRANSIENT).retryableUnavailable(PERMANENT)).isTrue();
        assertThat(closed(RecommendationReason.GENERATION_FAILED, TRANSIENT).retryableUnavailable(PERMANENT)).isTrue();
        assertThat(closed(RecommendationReason.ANALYSIS_FAILED, PERMANENT).retryableUnavailable(PERMANENT)).isFalse();
        assertThat(closed(RecommendationReason.GENERATION_FAILED, PERMANENT).retryableUnavailable(PERMANENT)).isFalse();
    }

    @Test
    @DisplayName("권한·소유로 닫힌 요청과 UNAVAILABLE 이 아닌 요청은 새로 시도하지 않는다")
    void givenPermanentOrNotUnavailable_whenRetryable_thenFalse() {
        // given // when // then
        assertThat(closed(RecommendationReason.OWN_VIDEO, TRANSIENT).retryableUnavailable(PERMANENT)).isFalse();
        assertThat(closed(RecommendationReason.BLOCKED, TRANSIENT).retryableUnavailable(PERMANENT)).isFalse();
        assertThat(closed(RecommendationReason.OWNER_LEFT, TRANSIENT).retryableUnavailable(PERMANENT)).isFalse();
        assertThat(RecommendationRequest.pending(1L, 2L, 3L, 4L, "v1", false, RecommendationRequest.NO_PERSONAL_HASH, NOW)
                .retryableUnavailable(PERMANENT)).isFalse();
    }
}

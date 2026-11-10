package org.son.haruharu.domain.replyrecommendation.implement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.replyrecommendation.implement.exception.RecommendationRateLimitedException;

class EnsureRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-11-08T05:00:00Z");

    private static EnsureRateLimiter limiter(int perMinute) {
        return new EnsureRateLimiter(new ReplyRecommendationProperties(true, false, perMinute, 1000,
                Duration.ofMinutes(30), Duration.ofHours(24),
                new ReplyRecommendationProperties.Collection(false, List.of()),
                new ReplyRecommendationProperties.Snapshot(3000, 20, 5, Duration.ofDays(30)), List.of()));
    }

    @Test
    @DisplayName("1분에 한도만큼만 새 작업을 허용하고, 1분이 지나면 다시 허용한다(429)")
    void givenLimit_whenAcquireRepeatedly_thenRejectsUntilWindowPasses() {
        var limiter = limiter(2);
        limiter.acquire(1L, NOW);
        limiter.acquire(1L, NOW.plusSeconds(10));

        assertThatThrownBy(() -> limiter.acquire(1L, NOW.plusSeconds(20)))
                .isInstanceOf(RecommendationRateLimitedException.class);
        assertThatCode(() -> limiter.acquire(2L, NOW.plusSeconds(20))).doesNotThrowAnyException();
        assertThatCode(() -> limiter.acquire(1L, NOW.plusSeconds(61))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한도 0 이하는 제한하지 않는다")
    void givenNonPositiveLimit_whenAcquire_thenNeverRejects() {
        var limiter = limiter(0);
        for (int i = 0; i < 50; i++) {
            limiter.acquire(1L, NOW);
        }
    }
}

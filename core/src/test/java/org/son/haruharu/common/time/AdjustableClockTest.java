package org.son.haruharu.common.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AdjustableClockTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant REAL = LocalDateTime.of(2026, 7, 10, 14, 0)
            .atZone(SEOUL).toInstant();

    private Clock realClock(Instant instant) {
        return Clock.fixed(instant, SEOUL);
    }

    @Test
    @DisplayName("고정하지 않으면 위임 시계의 현재 시각을 그대로 반환한다")
    void givenNoFixation_whenInstant_thenDelegates() {
        AdjustableClock clock = new AdjustableClock(realClock(REAL));

        assertThat(clock.instant()).isEqualTo(REAL);
        assertThat(clock.isFixed()).isFalse();
    }

    @Test
    @DisplayName("가상 시각을 고정하면 고정된 시각을 반환한다")
    void givenFixation_whenInstant_thenReturnsFixedInstant() {
        AdjustableClock clock = new AdjustableClock(realClock(REAL));
        LocalDateTime virtual = LocalDateTime.of(2026, 7, 12, 19, 0);

        clock.fix(virtual);

        assertThat(LocalDateTime.now(clock)).isEqualTo(virtual);
        assertThat(clock.isFixed()).isTrue();
        assertThat(clock.fixedAt()).contains(virtual);
    }

    @Test
    @DisplayName("리셋하면 실시간으로 복귀한다")
    void givenFixation_whenReset_thenBackToRealTime() {
        AdjustableClock clock = new AdjustableClock(realClock(REAL));
        clock.fix(LocalDateTime.of(2026, 7, 12, 19, 0));

        clock.reset();

        assertThat(clock.instant()).isEqualTo(REAL);
        assertThat(clock.isFixed()).isFalse();
        assertThat(clock.fixedAt()).isEmpty();
    }

    @Test
    @DisplayName("TTL이 지나면 자동으로 실시간으로 복귀한다")
    void givenExpiredTtl_whenInstant_thenAutoReverts() {
        MutableClock real = new MutableClock(REAL);
        AdjustableClock clock = new AdjustableClock(real);
        clock.fix(LocalDateTime.of(2026, 7, 12, 19, 0));

        real.advance(AdjustableClock.DEFAULT_TTL.plusMinutes(1));

        assertThat(clock.instant()).isEqualTo(real.instant());
        assertThat(clock.isFixed()).isFalse();
    }

    @Test
    @DisplayName("TTL 이내에는 고정이 유지된다")
    void givenTtlNotExpired_whenInstant_thenStaysFixed() {
        MutableClock real = new MutableClock(REAL);
        AdjustableClock clock = new AdjustableClock(real);
        LocalDateTime virtual = LocalDateTime.of(2026, 7, 12, 19, 0);
        clock.fix(virtual);

        real.advance(Duration.ofHours(3));

        assertThat(LocalDateTime.now(clock)).isEqualTo(virtual);
        assertThat(clock.isFixed()).isTrue();
    }

    @Test
    @DisplayName("고정 상태에서 만료 예정 시각(실시간 기준)을 조회할 수 있다")
    void givenFixation_whenExpiresAt_thenReturnsRealExpiry() {
        AdjustableClock clock = new AdjustableClock(realClock(REAL));
        clock.fix(LocalDateTime.of(2026, 7, 12, 19, 0));

        LocalDateTime expectedExpiry = LocalDateTime.ofInstant(
                REAL.plus(AdjustableClock.DEFAULT_TTL), SEOUL);
        assertThat(clock.expiresAt()).contains(expectedExpiry);
    }

    /** 테스트에서 실시간을 임의로 진행시키기 위한 가변 시계. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

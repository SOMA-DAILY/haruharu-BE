package org.son.haruharu.infrastructure.scheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.business.QuestionDeadlineNotifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class QuestionDeadlineSchedulerTest {
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock QuestionDeadlineNotifier notifier;

    /** 틱마다 시각을 옮겨 크론 발화 시점을 재현한다. */
    static final class MovingClock extends Clock {
        private Instant instant;

        MovingClock(LocalDateTime at) {
            moveTo(at);
        }

        void moveTo(LocalDateTime at) {
            this.instant = at.atZone(SEOUL).toInstant();
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
            return instant;
        }
    }

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 9, 14, hour, minute);
    }

    @Test
    @DisplayName("틱이 늦어 건너뛴 분은 다음 틱에서 채우고 같은 분을 두 번 보내지 않는다")
    void givenDelayedTick_whenScheduled_thenFillsTheGapWithoutRepeating() {
        MovingClock clock = new MovingClock(at(16, 0).withNano(12_000_000));
        QuestionDeadlineScheduler scheduler = new QuestionDeadlineScheduler(notifier, clock);

        scheduler.notifyClosingQuestions();
        clock.moveTo(at(16, 2).withSecond(31));
        scheduler.notifyClosingQuestions();

        ArgumentCaptor<LocalDateTime> minutes = ArgumentCaptor.forClass(LocalDateTime.class);
        then(notifier).should(times(3)).notifyClosingQuestions(minutes.capture());
        assertThat(minutes.getAllValues())
                .containsExactly(at(16, 0), at(16, 1), at(16, 2));
    }

    @Test
    @DisplayName("크론이 경계 직전에 발화해 같은 분이 다시 나와도 중복 발송하지 않는다")
    void givenEarlyFiring_whenScheduled_thenSkipsTheProcessedMinute() {
        MovingClock clock = new MovingClock(at(15, 59));
        QuestionDeadlineScheduler scheduler = new QuestionDeadlineScheduler(notifier, clock);

        scheduler.notifyClosingQuestions();
        clock.moveTo(at(15, 59).withSecond(59).withNano(988_000_000));
        scheduler.notifyClosingQuestions();
        clock.moveTo(at(16, 0));
        scheduler.notifyClosingQuestions();

        ArgumentCaptor<LocalDateTime> minutes = ArgumentCaptor.forClass(LocalDateTime.class);
        then(notifier).should(times(2)).notifyClosingQuestions(minutes.capture());
        assertThat(minutes.getAllValues()).containsExactly(at(15, 59), at(16, 0));
    }

    @Test
    @DisplayName("기동 직후에는 현재 분만 보고, 오래 멈췄다 살아나면 최근 분까지만 채운다")
    void givenStartUpOrLongPause_whenSelectingMinutes_thenBoundedCatchUp() {
        assertThat(QuestionDeadlineScheduler.pendingMinutes(null, at(16, 0)))
                .containsExactly(at(16, 0));
        assertThat(QuestionDeadlineScheduler.pendingMinutes(at(16, 0), at(16, 0))).isEmpty();
        assertThat(QuestionDeadlineScheduler.pendingMinutes(at(16, 1), at(16, 0))).isEmpty();
        assertThat(
                        QuestionDeadlineScheduler.pendingMinutes(
                                at(16, 0),
                                at(16, 0).plusMinutes(QuestionDeadlineScheduler.MAX_CATCH_UP_MINUTES)))
                .hasSize(QuestionDeadlineScheduler.MAX_CATCH_UP_MINUTES)
                .startsWith(at(16, 1));
        assertThat(QuestionDeadlineScheduler.pendingMinutes(at(15, 0), at(16, 0)))
                .hasSize(QuestionDeadlineScheduler.MAX_CATCH_UP_MINUTES)
                .endsWith(at(16, 0));
    }

    @Test
    @DisplayName("아무것도 보내지 못한 분은 스케줄러를 멈추지 않고 다음 틱에서 다시 시도한다")
    void givenFailingMinute_whenScheduled_thenRetriesItOnTheNextTick() {
        given(notifier.notifyClosingQuestions(at(16, 0))).willReturn(0);
        given(notifier.notifyClosingQuestions(at(16, 1)))
                .willThrow(new IllegalStateException("down"))
                .willReturn(0);
        given(notifier.notifyClosingQuestions(at(16, 2))).willReturn(0);
        MovingClock clock = new MovingClock(at(16, 0));
        QuestionDeadlineScheduler scheduler = new QuestionDeadlineScheduler(notifier, clock);

        scheduler.notifyClosingQuestions();
        clock.moveTo(at(16, 1));
        assertThatCode(scheduler::notifyClosingQuestions).doesNotThrowAnyException();
        clock.moveTo(at(16, 2));
        scheduler.notifyClosingQuestions();

        ArgumentCaptor<LocalDateTime> minutes = ArgumentCaptor.forClass(LocalDateTime.class);
        then(notifier).should(times(4)).notifyClosingQuestions(minutes.capture());
        assertThat(minutes.getAllValues())
                .containsExactly(at(16, 0), at(16, 1), at(16, 1), at(16, 2));
    }
}

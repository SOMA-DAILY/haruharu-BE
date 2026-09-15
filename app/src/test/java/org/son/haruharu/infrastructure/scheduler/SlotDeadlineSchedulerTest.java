package org.son.haruharu.infrastructure.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.business.SlotDeadlineNotifier;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlotDeadlineScheduler 시각 정규화")
class SlotDeadlineSchedulerTest {

    @Mock
    SlotDeadlineNotifier slotDeadlineNotifier;

    @Test
    @DisplayName("스케줄러가 notifier에 넘기는 now는 정분(분 단위 절삭)이다 — 나노초가 남으면 equals 비교가 항상 실패한다(#421)")
    void givenSchedulerTick_whenNotifyClosingSlots_thenPassesMinuteTruncatedNow() {
        // given
        SlotDeadlineScheduler scheduler = new SlotDeadlineScheduler(slotDeadlineNotifier);

        // when
        scheduler.notifyClosingSlots();

        // then
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(slotDeadlineNotifier).notifyClosingSlots(captor.capture());
        LocalDateTime passed = captor.getValue();
        assertThat(passed).isEqualTo(passed.truncatedTo(ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("cron이 :30 직전에 발화해도(실측 10:29:59.988 사례) 다음 정분으로 반올림된다 — 절삭이면 :29로 떨어져 미발송(#421)")
    void givenTickJustBeforeBoundary_whenNearestMinute_thenRoundsUp() {
        assertThat(SlotDeadlineScheduler.nearestMinute(LocalDateTime.parse("2026-09-15T10:29:59.988")))
                .isEqualTo(LocalDateTime.parse("2026-09-15T10:30:00"));
        assertThat(SlotDeadlineScheduler.nearestMinute(LocalDateTime.parse("2026-09-15T10:30:00.081234567")))
                .isEqualTo(LocalDateTime.parse("2026-09-15T10:30:00"));
    }
}

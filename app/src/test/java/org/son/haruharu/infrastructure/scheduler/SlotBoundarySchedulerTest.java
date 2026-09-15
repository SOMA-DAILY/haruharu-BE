package org.son.haruharu.infrastructure.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.business.SlotStartNotifier;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlotBoundaryScheduler 시각 정규화")
class SlotBoundarySchedulerTest {

    @Mock
    SlotStartNotifier slotStartNotifier;

    @Test
    @DisplayName("스케줄러가 notifier에 넘기는 now는 정각(시 단위 절삭)이다 — 나노초가 남으면 equals 비교가 항상 실패한다(#421)")
    void givenSchedulerTick_whenOnSlotBoundary_thenPassesHourTruncatedNow() {
        // given
        SlotBoundaryScheduler scheduler = new SlotBoundaryScheduler(slotStartNotifier);

        // when
        scheduler.onSlotBoundary();

        // then
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(slotStartNotifier).notifyOpeningSlots(captor.capture());
        LocalDateTime passed = captor.getValue();
        assertThat(passed).isEqualTo(passed.truncatedTo(ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("cron이 정각 직전에 발화해도(실측 10:29:59.988 사례) 다음 정각으로 반올림된다 — 절삭이면 이전 시각으로 떨어져 미발송(#421)")
    void givenTickJustBeforeBoundary_whenNearestHour_thenRoundsUp() {
        assertThat(SlotBoundaryScheduler.nearestHour(LocalDateTime.parse("2026-09-15T10:59:59.988")))
                .isEqualTo(LocalDateTime.parse("2026-09-15T11:00:00"));
        assertThat(SlotBoundaryScheduler.nearestHour(LocalDateTime.parse("2026-09-15T11:00:00.081234567")))
                .isEqualTo(LocalDateTime.parse("2026-09-15T11:00:00"));
    }
}

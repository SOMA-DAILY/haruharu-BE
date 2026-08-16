package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotClockTest {

    private static LocalDateTime at(int h, int m) {
        return LocalDateTime.of(2026, 6, 26, h, m);
    }

    @Test
    @DisplayName("현재 슬롯은 starts_at 상대 정시(starts_at + N시간)로 판정한다")
    void givenStartsAtOnTheHour_whenCurrentSlotStart_thenFloorToHourFromStart() {
        LocalDateTime startsAt = at(19, 0);

        assertThat(SlotClock.currentSlotStart(startsAt, at(19, 0))).isEqualTo(at(19, 0));
        assertThat(SlotClock.currentSlotStart(startsAt, at(19, 59))).isEqualTo(at(19, 0));
        assertThat(SlotClock.currentSlotStart(startsAt, at(20, 30))).isEqualTo(at(20, 0));
        assertThat(SlotClock.currentSlotStart(startsAt, at(21, 0))).isEqualTo(at(21, 0));
    }

    @Test
    @DisplayName("starts_at이 정시가 아니면 슬롯 경계도 starts_at 기준으로 어긋난 정시가 된다")
    void givenStartsAtOffHour_whenCurrentSlotStart_thenRelativeToStart() {
        LocalDateTime startsAt = at(19, 30);

        // 19:30 기준 +1시간 경계 = 20:30. now=21:15 → floor(1.75h)=1h → 20:30
        assertThat(SlotClock.currentSlotStart(startsAt, at(21, 15))).isEqualTo(at(20, 30));
        assertThat(SlotClock.currentSlotStart(startsAt, at(19, 45))).isEqualTo(at(19, 30));
    }

    @Test
    @DisplayName("슬롯은 slot_start부터 3시간 동안(가시성 창) 노출된다")
    void givenSlotStart_whenIsVisible_thenWithinThreeHours() {
        LocalDateTime slotStart = at(20, 0);

        assertThat(SlotClock.isVisible(slotStart, at(20, 0))).isTrue();
        assertThat(SlotClock.isVisible(slotStart, at(22, 59))).isTrue();
        assertThat(SlotClock.isVisible(slotStart, at(23, 0))).isFalse(); // +3h 경계는 만료
        assertThat(SlotClock.isVisible(slotStart, at(19, 59))).isFalse(); // 시작 전
    }

    @Test
    @DisplayName("가시 슬롯 목록은 현재 슬롯과 노출 중인 과거 슬롯(최대 2개), starts_at 이전은 제외한다")
    void givenNow_whenVisibleSlotStarts_thenCurrentAndVisiblePast() {
        LocalDateTime startsAt = at(19, 0);

        // now=22:10 → 현재 22:00, 과거 21:00·20:00 모두 3h 내 & starts_at 이후
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(22, 10)))
                .containsExactly(at(20, 0), at(21, 0), at(22, 0));

        // now=20:10 → 현재 20:00, 과거 19:00; 18:00은 starts_at 이전이라 제외
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(20, 10)))
                .containsExactly(at(19, 0), at(20, 0));

        // 시작 직후 → 현재 슬롯 하나만
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(19, 30)))
                .containsExactly(at(19, 0));
    }

    @Test
    @DisplayName("다음 슬롯 시작 시각은 현재 슬롯 + 1시간이다")
    void givenNow_whenNextSlotStart_thenPlusOneHour() {
        LocalDateTime startsAt = at(19, 0);
        assertThat(SlotClock.nextSlotStart(startsAt, at(20, 30))).isEqualTo(at(21, 0));
    }

    @Test
    @DisplayName("업로드 가능 상한(최종선택 오픈) 이상에서 시작하는 슬롯은 가시 창에서 제외된다")
    void givenUploadableUntil_whenVisibleSlotStarts_thenCapsAtBound() {
        LocalDateTime startsAt = at(19, 0);
        // now=21:30(최종선택 진행 중), opensAt=21:00 → 현재 슬롯 21:00은 업로드 불가라 제외, 19:00·20:00만 노출
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(21, 30), at(21, 0)))
                .containsExactly(at(19, 0), at(20, 0));

        // 상한 null이면 캡하지 않는다
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(21, 30), null))
                .containsExactly(at(19, 0), at(20, 0), at(21, 0));
    }
}

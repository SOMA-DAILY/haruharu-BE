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
    @DisplayName("슬롯은 해당 시간대에만 노출되고 다음 슬롯 시작 시 만료된다")
    void givenSlotStart_whenIsVisible_thenWithinOneHour() {
        LocalDateTime slotStart = at(20, 0);

        assertThat(SlotClock.isVisible(slotStart, at(20, 0))).isTrue();
        assertThat(SlotClock.isVisible(slotStart, at(21, 0).minusNanos(1))).isTrue();
        assertThat(SlotClock.isVisible(slotStart, at(21, 0))).isFalse();
        assertThat(SlotClock.isVisible(slotStart, at(19, 59))).isFalse(); // 시작 전
    }

    @Test
    @DisplayName("가시 슬롯 목록은 현재 슬롯만 포함하고 시작 전에는 비어 있다")
    void givenNow_whenVisibleSlotStarts_thenCurrentOnly() {
        LocalDateTime startsAt = at(19, 0);

        assertThat(SlotClock.visibleSlotStarts(startsAt, at(22, 10)))
                .containsExactly(at(22, 0));

        assertThat(SlotClock.visibleSlotStarts(startsAt, at(20, 10)))
                .containsExactly(at(20, 0));

        // 시작 직후 → 현재 슬롯 하나만
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(19, 30)))
                .containsExactly(at(19, 0));
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(18, 59))).isEmpty();
    }

    @Test
    @DisplayName("정시가 아닌 방 시작 시각에도 다음 슬롯 경계에서 이전 영상 노출이 끝난다")
    void givenOffHourStart_whenSlotChanges_thenOnlyNewSlotVisible() {
        LocalDateTime startsAt = at(19, 30);
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(20, 30).minusNanos(1)))
                .containsExactly(startsAt);
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(20, 30)))
                .containsExactly(at(20, 30));
        assertThat(SlotClock.isVisible(startsAt, at(20, 30))).isFalse();
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
        // 최종선택 오픈과 함께 현재 슬롯이 업로드 불가가 되고 이전 슬롯도 만료된다.
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(21, 30), at(21, 0)))
                .isEmpty();

        // 상한 null이면 캡하지 않는다
        assertThat(SlotClock.visibleSlotStarts(startsAt, at(21, 30), null))
                .containsExactly(at(21, 0));
    }

    // ── pastSlotStarts: 과거 회고 페이지네이션(#350) ──────────────────────────

    @Test
    @DisplayName("before 이전 슬롯을 최대 size개 오름차순으로, starts_at 미만은 제외한다")
    void givenBefore_whenPastSlotStarts_thenOlderSlotsAscending() {
        LocalDateTime startsAt = at(10, 0);
        // before=20:00, size=3 → 바로 아래 19:00부터 3개(17·18·19)
        assertThat(SlotClock.pastSlotStarts(startsAt, at(20, 0), 3, null))
                .containsExactly(at(17, 0), at(18, 0), at(19, 0));
    }

    @Test
    @DisplayName("과거로 방 시작에 닿으면 starts_at 이전은 잘려 개수가 줄어든다")
    void givenReachStart_whenPastSlotStarts_thenClampedAtStart() {
        LocalDateTime startsAt = at(18, 0);
        // before=20:00, size=6 → [18,19]만(17:00 이하는 starts_at 이전)
        assertThat(SlotClock.pastSlotStarts(startsAt, at(20, 0), 6, null))
                .containsExactly(at(18, 0), at(19, 0));
    }

    @Test
    @DisplayName("before가 정시가 아니어도 그리드 위 '그보다 과거' 슬롯을 고른다")
    void givenOffGridBefore_whenPastSlotStarts_thenGridAligned() {
        LocalDateTime startsAt = at(10, 0);
        // before=20:30 → 20:00은 before 미만이라 포함, 위에서부터 18·19·20
        assertThat(SlotClock.pastSlotStarts(startsAt, at(20, 30), 3, null))
                .containsExactly(at(18, 0), at(19, 0), at(20, 0));
    }

    @Test
    @DisplayName("before가 starts_at 이하거나 size≤0이면 빈 목록이다")
    void givenBoundary_whenPastSlotStarts_thenEmpty() {
        LocalDateTime startsAt = at(19, 0);
        assertThat(SlotClock.pastSlotStarts(startsAt, at(19, 0), 6, null)).isEmpty(); // before==startsAt
        assertThat(SlotClock.pastSlotStarts(startsAt, at(18, 0), 6, null)).isEmpty(); // before<startsAt
        assertThat(SlotClock.pastSlotStarts(startsAt, at(22, 0), 0, null)).isEmpty(); // size 0
    }

    @Test
    @DisplayName("before가 상한(최종선택 오픈)보다 뒤면 상한 직전부터 size개를 꽉 채워 시작한다(빈 페이지 조기 종료 방지)")
    void givenBeforeBeyondCutoff_whenPastSlotStarts_thenStartsFromCutoff() {
        LocalDateTime startsAt = at(10, 0);
        // before=23:00, opensAt=17:00, size=3 → 상한 직전(16:00)부터 [14,15,16]. clamp 없으면 [20,21,22]가 전부 잘려 빈 목록
        assertThat(SlotClock.pastSlotStarts(startsAt, at(23, 0), 3, at(17, 0)))
                .containsExactly(at(14, 0), at(15, 0), at(16, 0));
        // size=6이면 상한 직전 6개를 채운다 → [11..16] (clamp 전엔 filter로 3개만 남던 자리)
        assertThat(SlotClock.pastSlotStarts(startsAt, at(20, 0), 6, at(17, 0)))
                .containsExactly(at(11, 0), at(12, 0), at(13, 0), at(14, 0), at(15, 0), at(16, 0));
    }
}

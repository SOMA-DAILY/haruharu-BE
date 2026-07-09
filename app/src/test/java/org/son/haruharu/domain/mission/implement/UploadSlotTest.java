package org.son.haruharu.domain.mission.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UploadSlotTest {

    @Test
    @DisplayName("슬롯은 총 13개다 (Day1 5개 · Day2 5개 · Day3 3개)")
    void givenConfirmedSpec_whenValues_thenThirteenSlots() {
        assertThat(UploadSlot.values()).hasSize(13);
        assertThat(UploadSlot.ofDay(1)).hasSize(5);
        assertThat(UploadSlot.ofDay(2)).hasSize(5);
        assertThat(UploadSlot.ofDay(3)).hasSize(3);
    }

    @Test
    @DisplayName("Day3 슬롯은 1~3(09-12/12-15/15-18)만 존재하고 4·5는 없다")
    void givenDay3_whenOfDay_thenOnlyThreeSlotsUntil18() {
        List<UploadSlot> day3 = UploadSlot.ofDay(3);

        assertThat(day3).extracting(UploadSlot::slotIndex).containsExactly(1, 2, 3);
        assertThat(day3.getLast().endHour()).isEqualTo(18);
        assertThat(UploadSlot.of(3, 4)).isEmpty();
        assertThat(UploadSlot.of(3, 5)).isEmpty();
    }
}

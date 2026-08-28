package org.son.haruharu.domain.mission.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.implement.exception.InvalidMissionRevisionEligibilityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionRevisionEligibilityTest {

    @Test
    @DisplayName("Day 1~3과 오전·오후 조합을 eligibility로 만든다")
    void givenValidDayAndPeriod_whenCreate_thenCreatesEligibility() {
        MissionRevisionEligibility eligibility = MissionRevisionEligibility.of(10L, 2, MissionPeriod.AFTERNOON);

        assertThat(eligibility.dayNumber()).isEqualTo(2);
        assertThat(eligibility.period()).isEqualTo(MissionPeriod.AFTERNOON);
    }

    @Test
    @DisplayName("Day 범위가 1~3 밖이면 거부한다")
    void givenInvalidDay_whenCreate_thenThrows() {
        assertThatThrownBy(() -> MissionRevisionEligibility.of(10L, 4, MissionPeriod.MORNING))
                .isInstanceOf(InvalidMissionRevisionEligibilityException.class);
    }
}

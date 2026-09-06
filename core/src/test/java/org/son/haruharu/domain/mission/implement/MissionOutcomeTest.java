package org.son.haruharu.domain.mission.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MissionOutcomeTest {

    private static final LocalDateTime CLOSES_AT = LocalDateTime.of(2026, 9, 1, 18, 0);
    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 9, 1, 18, 5);

    @Test
    @DisplayName("대상 6명 중 4명이 활동하면 방 활성화 커버리지가 0.6667로 확정된다")
    void givenFourOfSixActivated_whenConfirmed_thenCoverageIsTwoThirds() {
        // given
        MissionMemberStateSummary summary =
                new MissionMemberStateSummary(6, 5, 4, 3, 1, 1);

        // when
        MissionOutcome outcome = MissionOutcome.confirmed(
                10L, MissionOutcome.METRIC_SCHEMA_V1, summary, CLOSES_AT, CALCULATED_AT);

        // then
        assertThat(outcome.roomActivationCoverage()).isEqualByComparingTo(new BigDecimal("0.6667"));
        assertThat(outcome.exposureReach()).isEqualByComparingTo(new BigDecimal("0.8333"));
        assertThat(outcome.targetMemberCount()).isEqualTo(6);
        assertThat(outcome.activatedMemberCount()).isEqualTo(4);
        assertThat(outcome.roomMissionId()).isEqualTo(10L);
        assertThat(outcome.metricSchemaVersion()).isEqualTo(MissionOutcome.METRIC_SCHEMA_V1);
        assertThat(outcome.closesAt()).isEqualTo(CLOSES_AT);
        assertThat(outcome.calculatedAt()).isEqualTo(CALCULATED_AT);
        assertThat(outcome.id()).isNull();
    }

    @Test
    @DisplayName("대상 멤버가 0명이면 계산이 실패하지 않고 커버리지가 0으로 기록된다")
    void givenNoTargetMember_whenConfirmed_thenCoverageIsZero() {
        // given
        MissionMemberStateSummary summary = MissionMemberStateSummary.empty();

        // when
        MissionOutcome outcome = MissionOutcome.confirmed(
                10L, MissionOutcome.METRIC_SCHEMA_V1, summary, CLOSES_AT, CALCULATED_AT);

        // then
        assertThat(outcome.roomActivationCoverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outcome.exposureReach()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outcome.targetMemberCount()).isZero();
    }

    @Test
    @DisplayName("전원이 활동하면 커버리지가 1.0000이다")
    void givenAllActivated_whenConfirmed_thenCoverageIsOne() {
        // given
        MissionMemberStateSummary summary =
                new MissionMemberStateSummary(6, 6, 6, 6, 0, 0);

        // when
        MissionOutcome outcome = MissionOutcome.confirmed(
                10L, MissionOutcome.METRIC_SCHEMA_V1, summary, CLOSES_AT, CALCULATED_AT);

        // then
        assertThat(outcome.roomActivationCoverage()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("비율은 소수점 넷째 자리까지 반올림해 저장 스케일과 맞춘다")
    void givenIndivisibleCounts_whenRatio_thenScaledToFour() {
        // when
        BigDecimal ratio = MissionOutcome.ratio(1, 3);

        // then
        assertThat(ratio.scale()).isEqualTo(MissionOutcome.RATIO_SCALE);
        assertThat(ratio).isEqualByComparingTo(new BigDecimal("0.3333"));
    }

    @Test
    @DisplayName("분모가 0이면 비율은 0을 반환한다")
    void givenZeroDenominator_whenRatio_thenZero() {
        // when
        BigDecimal ratio = MissionOutcome.ratio(3, 0);

        // then
        assertThat(ratio).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

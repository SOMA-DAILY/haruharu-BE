package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.MissionOutcome;
import org.son.haruharu.domain.mission.implement.MissionOutcomeReader;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MissionOutcomeBatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 18, 10);
    private static final Duration SETTLE_DELAY = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 100;

    @Mock RoomMissionReader roomMissionReader;
    @Mock MissionOutcomeReader outcomeReader;
    @Mock MissionOutcomeService outcomeService;

    MissionOutcomeBatchService batchService;

    @BeforeEach
    void setUp() {
        batchService = new MissionOutcomeBatchService(roomMissionReader, outcomeReader, outcomeService);
    }

    private RoomMission roomMission(Long id) {
        LocalDateTime opensAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        return new RoomMission(id, 1L, 100L, opensAt, LocalDateTime.of(2026, 9, 1, 18, 0), 0, opensAt);
    }

    @Test
    @DisplayName("정착 지연만큼 이전에 닫힌 미션만 집계 대상으로 조회한다")
    void givenSettleDelay_whenProcess_thenQueriesWithDelayedCutoff() {
        // given
        given(roomMissionReader.getClosedWithoutOutcome(
                any(), eq(MissionOutcome.METRIC_SCHEMA_V1), eq(BATCH_SIZE)))
                .willReturn(List.of());

        // when
        batchService.processClosedMissions(NOW, SETTLE_DELAY, BATCH_SIZE);

        // then
        verify(roomMissionReader).getClosedWithoutOutcome(
                NOW.minus(SETTLE_DELAY), MissionOutcome.METRIC_SCHEMA_V1, BATCH_SIZE);
    }

    @Test
    @DisplayName("확정된 미션과 이미 확정돼 스킵된 미션이 각각 집계된다")
    void givenMixedMissions_whenProcess_thenCountsConfirmedAndSkipped() {
        // given
        RoomMission first = roomMission(1L);
        RoomMission second = roomMission(2L);
        given(roomMissionReader.getClosedWithoutOutcome(any(), any(), eq(BATCH_SIZE)))
                .willReturn(List.of(first, second));
        given(outcomeService.confirm(first, NOW)).willReturn(Optional.of(outcome()));
        given(outcomeService.confirm(second, NOW)).willReturn(Optional.empty());

        // when
        MissionOutcomeBatchResult result = batchService.processClosedMissions(NOW, SETTLE_DELAY, BATCH_SIZE);

        // then
        assertThat(result.examined()).isEqualTo(2);
        assertThat(result.confirmed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("무결성 위반 뒤 실제로 확정돼 있으면 동시 실행 레이스로 보고 멱등 스킵한다")
    void givenUniqueViolationAndConfirmed_whenProcess_thenCountsAsSkipped() {
        // given
        RoomMission first = roomMission(1L);
        given(roomMissionReader.getClosedWithoutOutcome(any(), any(), eq(BATCH_SIZE)))
                .willReturn(List.of(first));
        willThrow(new DataIntegrityViolationException("duplicate key"))
                .given(outcomeService).confirm(first, NOW);
        given(outcomeReader.isConfirmed(first.id(), MissionOutcome.METRIC_SCHEMA_V1))
                .willReturn(true);

        // when
        MissionOutcomeBatchResult result = batchService.processClosedMissions(NOW, SETTLE_DELAY, BATCH_SIZE);

        // then
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("무결성 위반인데 확정되지 않았으면 레이스가 아니라 실패로 집계한다")
    void givenIntegrityViolationWithoutConfirmation_whenProcess_thenCountsAsFailed() {
        // given — 예: EXPIRED CHECK 제약 마이그레이션 미적용
        RoomMission first = roomMission(1L);
        given(roomMissionReader.getClosedWithoutOutcome(any(), any(), eq(BATCH_SIZE)))
                .willReturn(List.of(first));
        willThrow(new DataIntegrityViolationException("check constraint violated"))
                .given(outcomeService).confirm(first, NOW);
        given(outcomeReader.isConfirmed(first.id(), MissionOutcome.METRIC_SCHEMA_V1))
                .willReturn(false);

        // when
        MissionOutcomeBatchResult result = batchService.processClosedMissions(NOW, SETTLE_DELAY, BATCH_SIZE);

        // then
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("확정 여부 재확인마저 실패하면 스킵으로 위장하지 않고 실패로 집계한다")
    void givenRecheckFailure_whenProcess_thenCountsAsFailed() {
        // given
        RoomMission first = roomMission(1L);
        given(roomMissionReader.getClosedWithoutOutcome(any(), any(), eq(BATCH_SIZE)))
                .willReturn(List.of(first));
        willThrow(new DataIntegrityViolationException("duplicate key"))
                .given(outcomeService).confirm(first, NOW);
        willThrow(new org.springframework.dao.QueryTimeoutException("recheck down"))
                .given(outcomeReader).isConfirmed(first.id(), MissionOutcome.METRIC_SCHEMA_V1);

        // when
        MissionOutcomeBatchResult result = batchService.processClosedMissions(NOW, SETTLE_DELAY, BATCH_SIZE);

        // then
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 미션의 집계가 실패해도 나머지 미션 집계는 계속 진행한다")
    void givenOneFailure_whenProcess_thenContinuesRemaining() {
        // given
        RoomMission failing = roomMission(1L);
        RoomMission healthy = roomMission(2L);
        given(roomMissionReader.getClosedWithoutOutcome(any(), any(), eq(BATCH_SIZE)))
                .willReturn(List.of(failing, healthy));
        willThrow(new IllegalStateException("boom")).given(outcomeService).confirm(failing, NOW);
        given(outcomeService.confirm(healthy, NOW)).willReturn(Optional.of(outcome()));

        // when
        MissionOutcomeBatchResult result = batchService.processClosedMissions(NOW, SETTLE_DELAY, BATCH_SIZE);

        // then
        assertThat(result.examined()).isEqualTo(2);
        assertThat(result.confirmed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    private MissionOutcome outcome() {
        return MissionOutcome.confirmed(
                1L, MissionOutcome.METRIC_SCHEMA_V1,
                org.son.haruharu.domain.mission.implement.MissionMemberStateSummary.empty(),
                LocalDateTime.of(2026, 9, 1, 18, 0), NOW);
    }
}

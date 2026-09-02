package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.exception.NoMissionCandidateException;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionCandidateSnapshot;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RandomAssignerV1Test {

    private static final LocalDateTime OPENS_AT = LocalDateTime.of(2026, 9, 2, 12, 0);
    private static final LocalDateTime CLOSES_AT = OPENS_AT.plusHours(5);

    private final RandomAssignerV1 assigner = new RandomAssignerV1();

    @Test
    @DisplayName("동일한 후보와 seed로 실행하면 같은 revision과 균등 propensity가 재현된다")
    void givenSameCandidatesAndSeed_whenAssignNext_thenReproducesSelectionAndProbability() {
        MissionAssignmentContext context = context(
                List.of(revision(3L, false), revision(1L, false), revision(2L, false)),
                List.of(), 938_521L, OPENS_AT.minusMinutes(30));

        MissionDecision first = assigner.assignNext(context);
        MissionDecision replay = assigner.assignNext(context);

        assertThat(first.roomMission().missionRevisionId())
                .isEqualTo(replay.roomMission().missionRevisionId());
        assertThat(first.candidateSnapshot().candidates())
                .extracting(MissionCandidateSnapshot.Candidate::missionRevisionId)
                .containsExactly(1L, 2L, 3L);
        assertThat(first.candidateSnapshot().candidates())
                .allSatisfy(candidate -> assertThat(candidate.propensity())
                        .isEqualByComparingTo(new BigDecimal("0.333333333333")));
        assertThat(first.selectedProbability()).isEqualByComparingTo(new BigDecimal("0.333333333333"));
        assertThat(first.policyVersion()).isEqualTo("RANDOM_V1");
        assertThat(first.rngAlgorithm()).isEqualTo("SPLITTABLE_RANDOM_V1");
        assertThat(first.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.NONE);
        assertThat(first.roomMission().roomMissionSlotId()).isEqualTo(100L);
        assertThat(first.roomMission().opensAt()).isEqualTo(OPENS_AT);
        assertThat(first.roomMission().closesAt()).isEqualTo(CLOSES_AT);
    }

    @Test
    @DisplayName("일반 후보가 없으면 안전 fallback을 배정하고 DEGRADED 판단용 수준을 남긴다")
    void givenOnlyFallback_whenAssignNext_thenUsesSafeFallback() {
        MissionAssignmentContext context = context(
                List.of(), List.of(revision(9L, true)), 12L, OPENS_AT.plusMinutes(2));

        MissionDecision decision = assigner.assignNext(context);

        assertThat(decision.roomMission().missionRevisionId()).isEqualTo(9L);
        assertThat(decision.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.SAFE_FALLBACK);
        assertThat(decision.selectedProbability()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(decision.assignmentDelaySeconds()).isEqualTo(120L);
    }

    @Test
    @DisplayName("일반 후보와 fallback이 모두 없으면 빈 배정을 반환하지 않고 실패한다")
    void givenNoCandidate_whenAssignNext_thenThrows() {
        MissionAssignmentContext context = context(List.of(), List.of(), 12L, OPENS_AT.minusMinutes(10));

        assertThatThrownBy(() -> assigner.assignNext(context))
                .isInstanceOf(NoMissionCandidateException.class);
    }

    private MissionAssignmentContext context(List<MissionRevision> candidates,
                                               List<MissionRevision> fallbackCandidates,
                                               long seed,
                                               LocalDateTime now) {
        Room room = new Room(10L, RoomStatus.ACTIVE, LocalDate.of(2026, 9, 2), 3,
                OPENS_AT.minusHours(3), null, OPENS_AT.minusHours(3),
                OPENS_AT.plusDays(2), OPENS_AT.plusDays(2).plusHours(2),
                OPENS_AT.plusDays(2).plusHours(3), OPENS_AT.plusDays(3));
        RoomMissionSlot slot = new RoomMissionSlot(
                100L, room.id(), 1, MissionPeriod.MORNING, OPENS_AT, CLOSES_AT, OPENS_AT.minusDays(1));
        return new MissionAssignmentContext(
                room, slot, 0, List.of(), List.of(), candidates, List.of(), fallbackCandidates,
                org.son.haruharu.domain.mission.implement.RoomStateSnapshot.empty(6),
                "context-v2", now, now, "catalog-sha", "app-sha", seed);
    }

    private MissionRevision revision(Long id, boolean fallback) {
        return new MissionRevision(id, id, 1, "미션 " + id,
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "CLUSTER_" + id, new BigDecimal("0.8"), fallback, null);
    }
}

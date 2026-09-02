package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.exception.NoMissionCandidateException;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomStateSnapshot;
import org.son.haruharu.domain.room.implement.Room;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleBasedAssignerV1Test {

    private static final LocalDateTime OPENS_AT = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final LocalDateTime CLOSES_AT = OPENS_AT.plusHours(5);
    private static final LocalDateTime NOW = OPENS_AT.minusHours(1);

    private RuleBasedAssignerV1 assigner;

    @BeforeEach
    void setUp() {
        assigner = new RuleBasedAssignerV1();
    }

    @Test
    @DisplayName("Day 1 슬롯에는 자기개방 깊이가 상한을 넘는 미션이 eligibility 와 무관하게 배정되지 않는다")
    void givenDeepMissionEligibleForDayOne_whenAssign_thenExcluded() {
        // given — 운영자가 실수로 HIGH 깊이 미션에 Day 1 eligibility 를 준 상황
        MissionRevision deep = revision(1L, MissionAttributeLevel.HIGH, MissionGoal.PERSONAL,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, "DEEP");
        MissionRevision shallow = revision(2L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, "ROUTINE");

        // when
        var decision = assigner.assignNext(context(1, List.of(deep, shallow), List.of(), List.of(), List.of()));

        // then
        assertThat(decision.roomMission().missionRevisionId()).isEqualTo(2L);
        assertThat(decision.candidateSnapshot().candidates())
                .extracting(candidate -> candidate.missionRevisionId())
                .containsExactly(2L);
    }

    @Test
    @DisplayName("개인정보 위험과 호감 노출 위험이 높은 미션은 어떤 완화 단계에서도 제외된다")
    void givenRiskyMissions_whenOnlyRiskyRemain_thenFallsBackInsteadOfRelaxing() {
        // given — 위험 미션만 남았고 안전 fallback 이 있다
        MissionRevision privacy = revision(1L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.LOW, MissionAttributeLevel.HIGH, PreferenceLeakRisk.NONE, "PRIVACY");
        MissionRevision leak = revision(2L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.HIGH, "LEAK");
        MissionRevision safe = revision(9L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, "FB");

        // when
        var decision = assigner.assignNext(
                context(1, List.of(privacy, leak), List.of(), List.of(safe), List.of()));

        // then
        assertThat(decision.roomMission().missionRevisionId()).isEqualTo(9L);
        assertThat(decision.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.SAFE_FALLBACK);
    }

    @Test
    @DisplayName("직전 슬롯과 같은 의미 군집은 연속으로 배정되지 않는다")
    void givenSameClusterAsPrevious_whenAssign_thenPicksDifferentCluster() {
        // given
        MissionRevision previous = revision(1L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, "ROUTINE");
        MissionRevision sameCluster = revision(2L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, "ROUTINE");
        MissionRevision other = revision(3L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, "PLACE");

        // when
        var decision = assigner.assignNext(
                context(1, List.of(sameCluster, other), List.of(), List.of(), List.of(previous)));

        // then
        assertThat(decision.roomMission().missionRevisionId()).isEqualTo(3L);
        assertThat(decision.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.NONE);
    }

    @Test
    @DisplayName("같은 군집만 남으면 군집 제약을 풀고 완화 단계를 기록한다")
    void givenOnlySameCluster_whenAssign_thenRelaxesClusterAndRecords() {
        // given
        MissionRevision previous = revision(1L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, "ROUTINE");
        MissionRevision sameCluster = revision(2L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, "ROUTINE");

        // when
        var decision = assigner.assignNext(
                context(1, List.of(sameCluster), List.of(), List.of(), List.of(previous)));

        // then
        assertThat(decision.roomMission().missionRevisionId()).isEqualTo(2L);
        assertThat(decision.relaxationLevel())
                .isEqualTo(MissionAssignmentRelaxationLevel.CLUSTER_RELAXED);
    }

    @Test
    @DisplayName("같은 목표가 한도만큼 연속되면 그 목표는 더 이상 배정되지 않는다")
    void givenGoalRunAtLimit_whenAssign_thenPicksDifferentGoal() {
        // given — 직전 두 슬롯이 연속으로 OBSERVATION 이었다
        List<MissionRevision> previous = List.of(
                withGoalAndCluster(1L, MissionGoal.OBSERVATION, "A"),
                withGoalAndCluster(2L, MissionGoal.OBSERVATION, "B"));
        MissionRevision sameGoal = withGoalAndCluster(3L, MissionGoal.OBSERVATION, "C");
        MissionRevision otherGoal = withGoalAndCluster(4L, MissionGoal.PREFERENCE, "D");

        // when
        var decision = assigner.assignNext(
                context(1, List.of(sameGoal, otherGoal), List.of(), List.of(), previous));

        // then
        assertThat(decision.roomMission().missionRevisionId()).isEqualTo(4L);
        assertThat(decision.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.NONE);
    }

    @Test
    @DisplayName("연속이 중간에 끊겼으면 같은 목표를 다시 배정할 수 있다")
    void givenGoalRunBroken_whenAssign_thenSameGoalAllowed() {
        // given — OBSERVATION, PREFERENCE, OBSERVATION 순. 끝에서부터의 연속은 1회뿐이다
        List<MissionRevision> previous = List.of(
                withGoalAndCluster(1L, MissionGoal.OBSERVATION, "A"),
                withGoalAndCluster(2L, MissionGoal.PREFERENCE, "B"),
                withGoalAndCluster(3L, MissionGoal.OBSERVATION, "C"));
        MissionRevision sameGoal = withGoalAndCluster(4L, MissionGoal.OBSERVATION, "D");

        // when
        var decision = assigner.assignNext(
                context(1, List.of(sameGoal), List.of(), List.of(), previous));

        // then
        assertThat(decision.roomMission().missionRevisionId()).isEqualTo(4L);
        assertThat(decision.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.NONE);
    }

    @Test
    @DisplayName("목표 연속 제한에 걸린 후보만 남으면 제한을 풀고 FORMAT_RELAXED 로 기록한다")
    void givenOnlyGoalBlockedCandidates_whenAssign_thenRelaxesFormat() {
        // given — 목표가 한도만큼 연속됐고, 남은 후보는 같은 목표뿐이다(군집은 다름)
        List<MissionRevision> previous = List.of(
                withGoalAndCluster(1L, MissionGoal.OBSERVATION, "A"),
                withGoalAndCluster(2L, MissionGoal.OBSERVATION, "B"));
        MissionRevision sameGoal = withGoalAndCluster(3L, MissionGoal.OBSERVATION, "C");

        // when
        var decision = assigner.assignNext(
                context(1, List.of(sameGoal), List.of(), List.of(), previous));

        // then
        assertThat(decision.roomMission().missionRevisionId()).isEqualTo(3L);
        assertThat(decision.relaxationLevel())
                .isEqualTo(MissionAssignmentRelaxationLevel.FORMAT_RELAXED);
    }

    @Test
    @DisplayName("부담이 높은 후보만 남으면 부담 상한을 풀고 BURDEN_RELAXED 로 기록한다")
    void givenOnlyHighBurdenCandidates_whenAssign_thenRelaxesBurden() {
        // given
        MissionRevision heavy = revision(1L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.HIGH, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, "HEAVY");

        // when
        var decision = assigner.assignNext(context(1, List.of(heavy), List.of(), List.of(), List.of()));

        // then
        assertThat(decision.roomMission().missionRevisionId()).isEqualTo(1L);
        assertThat(decision.relaxationLevel())
                .isEqualTo(MissionAssignmentRelaxationLevel.BURDEN_RELAXED);
    }

    @Test
    @DisplayName("신규 후보가 없으면 이미 쓴 미션을 재사용하고 REUSE_RELAXED 로 기록한다")
    void givenNoFreshCandidates_whenAssign_thenReusesAndRecords() {
        // given
        MissionRevision used = revision(5L, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, "ROUTINE");

        // when
        var decision = assigner.assignNext(context(1, List.of(), List.of(used), List.of(), List.of()));

        // then
        assertThat(decision.roomMission().missionRevisionId()).isEqualTo(5L);
        assertThat(decision.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.REUSE_RELAXED);
    }

    @Test
    @DisplayName("후보도 fallback 도 없으면 조용히 넘기지 않고 예외를 던진다")
    void givenNothingAvailable_whenAssign_thenThrows() {
        assertThatThrownBy(() -> assigner.assignNext(context(1, List.of(), List.of(), List.of(), List.of())))
                .isInstanceOf(NoMissionCandidateException.class);
    }

    @Test
    @DisplayName("선택 확률은 균등이 아니라 순위 가중이며 기록된다")
    void givenMultipleCandidates_whenAssign_thenPropensityIsRankWeighted() {
        // given — 품질 점수만 다른 후보 3개
        List<MissionRevision> candidates = List.of(
                revisionWithQuality(1L, "A", new BigDecimal("0.9")),
                revisionWithQuality(2L, "B", new BigDecimal("0.7")),
                revisionWithQuality(3L, "C", new BigDecimal("0.5")));

        // when
        var decision = assigner.assignNext(context(1, candidates, List.of(), List.of(), List.of()));

        // then — 균등이면 1/3 이어야 한다. 순위 가중이므로 그렇지 않다.
        assertThat(decision.candidateSnapshot().candidates())
                .extracting(candidate -> candidate.propensity())
                .doesNotContain(new BigDecimal("0.333333333333"));
        assertThat(decision.selectedProbability()).isGreaterThan(BigDecimal.ZERO);
        BigDecimal sum = decision.candidateSnapshot().candidates().stream()
                .map(candidate -> candidate.propensity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).as("확률의 합은 1이어야 한다").isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("같은 시드와 같은 입력이면 같은 미션이 선택된다")
    void givenSameSeed_whenAssignTwice_thenSameSelection() {
        List<MissionRevision> candidates = List.of(
                revisionWithQuality(1L, "A", new BigDecimal("0.9")),
                revisionWithQuality(2L, "B", new BigDecimal("0.7")),
                revisionWithQuality(3L, "C", new BigDecimal("0.5")));

        var first = assigner.assignNext(context(1, candidates, List.of(), List.of(), List.of()));
        var second = assigner.assignNext(context(1, candidates, List.of(), List.of(), List.of()));

        assertThat(first.roomMission().missionRevisionId())
                .isEqualTo(second.roomMission().missionRevisionId());
    }

    @Test
    @DisplayName("방 상태는 스냅샷에 기록되지만 후보 점수를 바꾸지 않는다")
    void givenDifferentRoomStates_whenAssign_thenScoresUnchangedButRecorded() {
        List<MissionRevision> candidates = List.of(
                revisionWithQuality(1L, "A", new BigDecimal("0.9")),
                revisionWithQuality(2L, "B", new BigDecimal("0.5")));
        RoomStateSnapshot quiet = new RoomStateSnapshot(6, 2, 1,
                new BigDecimal("0.1000"), new BigDecimal("0.1000"));
        RoomStateSnapshot lively = new RoomStateSnapshot(6, 2, 6,
                new BigDecimal("0.9000"), new BigDecimal("0.9000"));

        var quietDecision = assigner.assignNext(
                context(1, candidates, List.of(), List.of(), List.of(), quiet));
        var livelyDecision = assigner.assignNext(
                context(1, candidates, List.of(), List.of(), List.of(), lively));

        assertThat(quietDecision.candidateSnapshot().candidates())
                .as("방 상태가 달라도 후보 확률은 같아야 한다 — 점수 입력이 아니다")
                .isEqualTo(livelyDecision.candidateSnapshot().candidates());
        assertThat(quietDecision.roomMission().contextSnapshot().roomState()).isEqualTo(quiet);
        assertThat(livelyDecision.roomMission().contextSnapshot().roomState()).isEqualTo(lively);
    }

    // --- fixtures ---

    private MissionAssignmentContext context(
            int dayNumber, List<MissionRevision> candidates, List<MissionRevision> reusable,
            List<MissionRevision> fallback, List<MissionRevision> previous) {
        return context(dayNumber, candidates, reusable, fallback, previous, RoomStateSnapshot.empty(6));
    }

    private MissionAssignmentContext context(
            int dayNumber, List<MissionRevision> candidates, List<MissionRevision> reusable,
            List<MissionRevision> fallback, List<MissionRevision> previous, RoomStateSnapshot roomState) {
        Room room = new Room(1L, null, null, 3, null, null);
        RoomMissionSlot slot = new RoomMissionSlot(
                10L, 1L, dayNumber, MissionPeriod.MORNING, OPENS_AT, CLOSES_AT, NOW);
        return new MissionAssignmentContext(
                room, slot, previous.size(),
                previous.stream().map(MissionRevision::id).toList(), previous,
                candidates, reusable, fallback, roomState,
                "context-v2", NOW, NOW, "catalog-test", "test-sha", 42L);
    }

    private MissionRevision revision(
            Long id, MissionAttributeLevel depth, MissionGoal goal, MissionAttributeLevel burden,
            MissionAttributeLevel privacy, PreferenceLeakRisk leak, String cluster) {
        return new MissionRevision(id, id * 100, 1, "미션 " + id, depth, goal,
                ActivationChannel.VIDEO, InteractionScope.SELF, burden, privacy, leak,
                MissionAttributeLevel.LOW, cluster, new BigDecimal("0.8"), false, NOW);
    }

    private MissionRevision withGoalAndCluster(Long id, MissionGoal goal, String cluster) {
        return revision(id, MissionAttributeLevel.LOW, goal, MissionAttributeLevel.LOW,
                MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE, cluster);
    }

    private MissionRevision revisionWithQuality(Long id, String cluster, BigDecimal quality) {
        return new MissionRevision(id, id * 100, 1, "미션 " + id,
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION, ActivationChannel.VIDEO,
                InteractionScope.SELF, MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW, cluster, quality, false, NOW);
    }
}

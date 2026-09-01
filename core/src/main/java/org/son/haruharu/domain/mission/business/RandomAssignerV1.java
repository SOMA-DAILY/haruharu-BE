package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.business.exception.NoMissionCandidateException;
import org.son.haruharu.domain.mission.implement.MissionAssignmentContextSnapshot;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionCandidateSnapshot;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

@Component
public class RandomAssignerV1 implements MissionAssigner {

    static final String POLICY_VERSION = "RANDOM_V1";
    static final String CANDIDATE_SCHEMA_VERSION = "candidate-v1";
    static final String RNG_ALGORITHM = "SPLITTABLE_RANDOM_V1";
    private static final int PROBABILITY_SCALE = 12;

    @Override
    public MissionDecision assignNext(MissionAssignmentContext context) {
        MissionAssignmentRelaxationLevel relaxationLevel = context.candidates().isEmpty()
                ? MissionAssignmentRelaxationLevel.SAFE_FALLBACK
                : MissionAssignmentRelaxationLevel.NONE;
        List<MissionRevision> pool = (relaxationLevel == MissionAssignmentRelaxationLevel.NONE
                ? context.candidates() : context.fallbackCandidates()).stream()
                .sorted(Comparator.comparing(MissionRevision::id))
                .toList();
        if (pool.isEmpty()) {
            throw new NoMissionCandidateException();
        }

        BigDecimal propensity = BigDecimal.ONE.divide(
                BigDecimal.valueOf(pool.size()), PROBABILITY_SCALE, RoundingMode.HALF_UP);
        MissionRevision selected = pool.get(new SplittableRandom(context.rngSeed()).nextInt(pool.size()));
        MissionCandidateSnapshot candidateSnapshot = new MissionCandidateSnapshot(pool.stream()
                .map(revision -> new MissionCandidateSnapshot.Candidate(
                        revision.id(), revision.missionId(), revision.revisionNumber(),
                        revision.fallback(), propensity))
                .toList());
        MissionAssignmentContextSnapshot contextSnapshot = new MissionAssignmentContextSnapshot(
                context.room().id(), context.missionSlot().id(), context.missionSlot().dayNumber(),
                context.missionSlot().period().name(), context.missionSlot().opensAt(),
                context.missionSlot().closesAt(), context.previousMissionRevisionIds(), context.contextAsOf());
        long delaySeconds = Math.max(0L, Duration.between(context.missionSlot().opensAt(), context.now()).getSeconds());
        UUID explorationId = UUID.nameUUIDFromBytes((context.missionSlot().id() + ":" + context.rngSeed())
                .getBytes(StandardCharsets.UTF_8));
        RoomMission roomMission = RoomMission.assigned(
                context.room().id(), context.missionSlot().id(), selected.id(),
                context.missionSlot().opensAt(), context.missionSlot().closesAt(), context.sequence(),
                POLICY_VERSION, context.catalogVersion(), context.contextSchemaVersion(), context.contextAsOf(),
                contextSnapshot, CANDIDATE_SCHEMA_VERSION, candidateSnapshot, propensity, explorationId,
                context.rngSeed(), RNG_ALGORITHM, relaxationLevel, context.applicationCommitSha(),
                context.now(), delaySeconds);
        return new MissionDecision(roomMission);
    }
}

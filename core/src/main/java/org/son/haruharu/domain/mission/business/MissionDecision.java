package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionCandidateSnapshot;
import org.son.haruharu.domain.mission.implement.RoomMission;

import java.math.BigDecimal;

public record MissionDecision(RoomMission roomMission) {

    public String policyVersion() {
        return roomMission.policyVersion();
    }

    public MissionCandidateSnapshot candidateSnapshot() {
        return roomMission.candidateSnapshot();
    }

    public BigDecimal selectedProbability() {
        return roomMission.selectedProbability();
    }

    public String rngAlgorithm() {
        return roomMission.rngAlgorithm();
    }

    public MissionAssignmentRelaxationLevel relaxationLevel() {
        return roomMission.relaxationLevel();
    }

    public long assignmentDelaySeconds() {
        return roomMission.assignmentDelaySeconds();
    }
}

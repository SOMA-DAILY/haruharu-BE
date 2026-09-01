package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;

public record MissionAssignmentExecution(
        Long roomMissionId,
        MissionAssignmentRelaxationLevel relaxationLevel,
        boolean skipped
) {
    public static MissionAssignmentExecution assigned(
            Long roomMissionId, MissionAssignmentRelaxationLevel relaxationLevel) {
        return new MissionAssignmentExecution(roomMissionId, relaxationLevel, false);
    }

    public static MissionAssignmentExecution skippedResult() {
        return new MissionAssignmentExecution(null, MissionAssignmentRelaxationLevel.NONE, true);
    }
}

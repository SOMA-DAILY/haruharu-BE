package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;
import java.util.List;

public record MissionAssignmentContextSnapshot(
        Long roomId,
        Long roomMissionSlotId,
        int dayNumber,
        String period,
        LocalDateTime opensAt,
        LocalDateTime closesAt,
        List<Long> previousMissionRevisionIds,
        LocalDateTime contextAsOf
) {
}

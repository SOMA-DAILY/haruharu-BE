package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;

public record MissionAssignmentIncident(
        Long id,
        Long roomMissionSlotId,
        MissionAssignmentIncidentStatus status,
        MissionAssignmentIncidentReason reason,
        int attemptCount,
        LocalDateTime firstFailedAt,
        LocalDateTime lastFailedAt,
        LocalDateTime resolvedAt,
        String lastErrorSummary,
        LocalDateTime createdAt
) {
    public static MissionAssignmentIncident first(
            Long roomMissionSlotId,
            MissionAssignmentIncidentStatus status,
            MissionAssignmentIncidentReason reason,
            String errorSummary,
            LocalDateTime now
    ) {
        return new MissionAssignmentIncident(
                null, roomMissionSlotId, status, reason, 1,
                now, now, status == MissionAssignmentIncidentStatus.RESOLVED ? now : null,
                errorSummary, null);
    }

    public MissionAssignmentIncident record(
            MissionAssignmentIncidentStatus nextStatus,
            MissionAssignmentIncidentReason nextReason,
            String errorSummary,
            LocalDateTime now
    ) {
        return new MissionAssignmentIncident(
                id, roomMissionSlotId, nextStatus, nextReason, attemptCount + 1,
                firstFailedAt, now, nextStatus == MissionAssignmentIncidentStatus.RESOLVED ? now : null,
                errorSummary, createdAt);
    }

    public MissionAssignmentIncident resolved(LocalDateTime now) {
        return new MissionAssignmentIncident(
                id, roomMissionSlotId, MissionAssignmentIncidentStatus.RESOLVED, reason, attemptCount,
                firstFailedAt, lastFailedAt, now, lastErrorSummary, createdAt);
    }
}

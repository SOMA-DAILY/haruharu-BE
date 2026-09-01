package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.room.implement.Room;

import java.time.LocalDateTime;
import java.util.List;

public record MissionAssignmentContext(
        Room room,
        RoomMissionSlot missionSlot,
        int sequence,
        List<Long> previousMissionRevisionIds,
        List<MissionRevision> candidates,
        List<MissionRevision> fallbackCandidates,
        String contextSchemaVersion,
        LocalDateTime contextAsOf,
        LocalDateTime now,
        String catalogVersion,
        String applicationCommitSha,
        long rngSeed
) {
    public MissionAssignmentContext {
        previousMissionRevisionIds = List.copyOf(previousMissionRevisionIds);
        candidates = List.copyOf(candidates);
        fallbackCandidates = List.copyOf(fallbackCandidates);
    }
}

package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.RoomStateSnapshot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.room.implement.Room;

import java.time.LocalDateTime;
import java.util.List;

public record MissionAssignmentContext(
        Room room,
        RoomMissionSlot missionSlot,
        int sequence,
        List<Long> previousMissionRevisionIds,
        /** 배정 순서대로의 이전 revision 실체. 연속 중복 판정에 속성이 필요해 ID 만으로는 부족하다. */
        List<MissionRevision> previousRevisions,
        List<MissionRevision> candidates,
        /** 이미 쓴 revision. REUSE_RELAXED 단계에서만 후보에 합류한다. */
        List<MissionRevision> previouslyUsedCandidates,
        List<MissionRevision> fallbackCandidates,
        /** 기록 전용. 정책 점수에 쓰지 않는다. */
        RoomStateSnapshot roomState,
        String contextSchemaVersion,
        LocalDateTime contextAsOf,
        LocalDateTime now,
        String catalogVersion,
        String applicationCommitSha,
        long rngSeed
) {
    public MissionAssignmentContext {
        previousMissionRevisionIds = List.copyOf(previousMissionRevisionIds);
        previousRevisions = List.copyOf(previousRevisions);
        candidates = List.copyOf(candidates);
        previouslyUsedCandidates = List.copyOf(previouslyUsedCandidates);
        fallbackCandidates = List.copyOf(fallbackCandidates);
    }
}

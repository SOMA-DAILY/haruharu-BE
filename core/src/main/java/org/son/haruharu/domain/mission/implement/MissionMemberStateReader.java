package org.son.haruharu.domain.mission.implement;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

@Component
public class MissionMemberStateReader {

    private final MissionMemberStateRepository repository;

    public MissionMemberStateReader(MissionMemberStateRepository repository) {
        this.repository = repository;
    }

    /** outcome 확정용 멤버 상태 집계. */
    public MissionMemberStateSummary summarize(Long roomMissionId) {
        return repository.summarize(roomMissionId);
    }

    public Set<Long> getResolvedRoomMissionIds(Long roomMemberId, Collection<Long> roomMissionIds) {
        if (roomMissionIds.isEmpty()) {
            return Set.of();
        }
        return repository.findResolvedRoomMissionIds(roomMemberId, roomMissionIds);
    }
}

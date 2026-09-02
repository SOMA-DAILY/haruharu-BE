package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

public interface MissionMemberStateRepository {

    int initializeOpenedTargets(LocalDateTime fromExclusive, LocalDateTime now, int batchSize);

    int initializeTarget(Long roomMissionId, Long roomMemberId);

    int initializeActivityTargets(Long roomId, Long roomMemberId, LocalDateTime occurredAt);

    int impress(Long roomMissionId, Long roomMemberId, LocalDateTime occurredAt);

    int dismiss(Long roomMissionId, Long roomMemberId, LocalDateTime occurredAt);

    int recordActivity(
            Long roomId, Long roomMemberId, ActivationChannel channel,
            MissionCardResolution resolution, LocalDateTime occurredAt);

    Set<Long> findResolvedRoomMissionIds(Long roomMemberId, Collection<Long> roomMissionIds);
}

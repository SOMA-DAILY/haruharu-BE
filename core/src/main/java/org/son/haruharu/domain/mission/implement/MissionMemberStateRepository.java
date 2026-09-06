package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

public interface MissionMemberStateRepository {

    int initializeOpenedTargets(LocalDateTime fromExclusive, LocalDateTime now, int batchSize);

    int initializeTarget(Long roomMissionId, Long roomMemberId);

    int initializeActivityTargets(Long roomId, Long roomMemberId, LocalDateTime occurredAt);

    int impress(Long roomMissionId, Long roomMemberId, LocalDateTime occurredAt);

    int recordActivity(
            Long roomId, Long roomMemberId, ActivationChannel channel,
            MissionCardResolution resolution, LocalDateTime occurredAt);

    Set<Long> findResolvedRoomMissionIds(Long roomMemberId, Collection<Long> roomMissionIds);

    /** 시간창이 닫힌 미션의 미해소 카드를 EXPIRED 로 마감한다. 반환값은 마감된 행 수. */
    int expireUnresolved(Long roomMissionId, LocalDateTime closesAt);

    /** outcome 확정용 멤버 상태 집계. 대상이 없으면 0으로 채워진 집계를 반환한다. */
    MissionMemberStateSummary summarize(Long roomMissionId);
}

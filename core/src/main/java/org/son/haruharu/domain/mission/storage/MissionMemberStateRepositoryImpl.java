package org.son.haruharu.domain.mission.storage;

import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.MissionCardResolution;
import org.son.haruharu.domain.mission.implement.MissionMemberStateRepository;
import org.son.haruharu.domain.mission.implement.MissionMemberStateSummary;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public class MissionMemberStateRepositoryImpl implements MissionMemberStateRepository {

    private final MissionMemberStateJpaRepository jpaRepository;

    public MissionMemberStateRepositoryImpl(MissionMemberStateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int initializeOpenedTargets(
            LocalDateTime fromExclusive, LocalDateTime now, int batchSize) {
        return jpaRepository.initializeOpenedTargets(fromExclusive, now, batchSize);
    }

    @Override
    public int initializeTarget(Long roomMissionId, Long roomMemberId) {
        return jpaRepository.initializeTarget(roomMissionId, roomMemberId);
    }

    @Override
    public int initializeActivityTargets(Long roomId, Long roomMemberId, LocalDateTime occurredAt) {
        return jpaRepository.initializeActivityTargets(roomId, roomMemberId, occurredAt);
    }

    @Override
    public int impress(Long roomMissionId, Long roomMemberId, LocalDateTime occurredAt) {
        return jpaRepository.impress(roomMissionId, roomMemberId, occurredAt);
    }

    @Override
    public int recordActivity(
            Long roomId, Long roomMemberId, ActivationChannel channel,
            MissionCardResolution resolution, LocalDateTime occurredAt) {
        return jpaRepository.recordActivity(roomId, roomMemberId, channel, resolution, occurredAt);
    }

    @Override
    public Set<Long> findResolvedRoomMissionIds(Long roomMemberId, Collection<Long> roomMissionIds) {
        return jpaRepository.findResolvedRoomMissionIds(roomMemberId, roomMissionIds);
    }

    @Override
    public int expireUnresolved(Long roomMissionId, LocalDateTime closesAt) {
        return jpaRepository.expireUnresolved(roomMissionId, closesAt);
    }

    @Override
    public MissionMemberStateSummary summarize(Long roomMissionId) {
        List<Object[]> rows = jpaRepository.summarize(roomMissionId);
        if (rows.isEmpty()) {
            return MissionMemberStateSummary.empty();
        }
        Object[] row = rows.getFirst();
        return new MissionMemberStateSummary(
                count(row, 0), count(row, 1), count(row, 2), count(row, 3),
                count(row, 4), count(row, 5));
    }

    /** COUNT 는 bigint 로 오므로 Number 로 받아 int 로 좁힌다. 방 인원 규모라 오버플로 위험은 없다. */
    private static int count(Object[] row, int index) {
        Object value = row[index];
        return value == null ? 0 : ((Number) value).intValue();
    }
}

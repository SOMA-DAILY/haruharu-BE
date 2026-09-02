package org.son.haruharu.domain.mission.storage;

import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.MissionCardResolution;
import org.son.haruharu.domain.mission.implement.MissionMemberStateRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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
    public int dismiss(Long roomMissionId, Long roomMemberId, LocalDateTime occurredAt) {
        return jpaRepository.dismiss(roomMissionId, roomMemberId, occurredAt);
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
}

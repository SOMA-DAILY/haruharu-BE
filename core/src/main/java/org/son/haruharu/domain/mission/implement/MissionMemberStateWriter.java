package org.son.haruharu.domain.mission.implement;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MissionMemberStateWriter {

    private final MissionMemberStateRepository repository;

    public MissionMemberStateWriter(MissionMemberStateRepository repository) {
        this.repository = repository;
    }

    public int initializeOpenedTargets(
            LocalDateTime fromExclusive, LocalDateTime now, int batchSize) {
        return repository.initializeOpenedTargets(fromExclusive, now, batchSize);
    }

    public int initializeTarget(Long roomMissionId, Long roomMemberId) {
        return repository.initializeTarget(roomMissionId, roomMemberId);
    }

    public int initializeActivityTargets(Long roomId, Long roomMemberId, LocalDateTime occurredAt) {
        return repository.initializeActivityTargets(roomId, roomMemberId, occurredAt);
    }

    /** 시간창 종료 시 미해소 카드를 EXPIRED 로 마감한다. */
    public int expireUnresolved(Long roomMissionId, LocalDateTime closesAt) {
        return repository.expireUnresolved(roomMissionId, closesAt);
    }

    public int impress(Long roomMissionId, Long roomMemberId, LocalDateTime occurredAt) {
        return repository.impress(roomMissionId, roomMemberId, occurredAt);
    }

    public int recordActivity(
            Long roomId, Long roomMemberId, ActivationChannel channel, LocalDateTime occurredAt) {
        return repository.recordActivity(
                roomId, roomMemberId, channel, MissionCardResolution.activity(channel), occurredAt);
    }
}

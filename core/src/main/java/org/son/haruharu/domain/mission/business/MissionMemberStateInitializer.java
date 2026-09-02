package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.MissionMemberStateWriter;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class MissionMemberStateInitializer {

    private static final Duration OPENED_TARGET_CATCH_UP_WINDOW = Duration.ofDays(3);

    private final MissionMemberStateWriter stateWriter;

    public MissionMemberStateInitializer(MissionMemberStateWriter stateWriter) {
        this.stateWriter = stateWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int initializeOpenedTargets(LocalDateTime now, int batchSize) {
        return stateWriter.initializeOpenedTargets(
                now.minus(OPENED_TARGET_CATCH_UP_WINDOW), now, batchSize);
    }

    @Transactional
    public int initializeTarget(RoomMission roomMission, RoomMember roomMember) {
        return stateWriter.initializeTarget(roomMission.id(), roomMember.id());
    }

    @Transactional
    public int initializeActivityTargets(Long roomId, Long roomMemberId, LocalDateTime occurredAt) {
        return stateWriter.initializeActivityTargets(roomId, roomMemberId, occurredAt);
    }
}

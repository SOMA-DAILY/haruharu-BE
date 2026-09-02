package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.MissionMemberStateWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class MissionActivityCollectorService {

    private final MissionMemberStateInitializer initializer;
    private final MissionMemberStateWriter stateWriter;
    private final RoomMemberReader roomMemberReader;

    public MissionActivityCollectorService(
            MissionMemberStateInitializer initializer,
            MissionMemberStateWriter stateWriter,
            RoomMemberReader roomMemberReader) {
        this.initializer = initializer;
        this.stateWriter = stateWriter;
        this.roomMemberReader = roomMemberReader;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordVideo(Long roomId, Long roomMemberId, LocalDateTime occurredAt) {
        initializer.initializeActivityTargets(roomId, roomMemberId, occurredAt);
        stateWriter.recordActivity(roomId, roomMemberId, ActivationChannel.VIDEO, occurredAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordGroupChat(Long roomId, Long senderUserId, LocalDateTime occurredAt) {
        RoomMember member = roomMemberReader.getByRoomIdAndUserId(roomId, senderUserId);
        initializer.initializeActivityTargets(roomId, member.id(), occurredAt);
        stateWriter.recordActivity(roomId, member.id(), ActivationChannel.GROUP_CHAT, occurredAt);
    }
}

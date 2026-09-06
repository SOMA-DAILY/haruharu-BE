package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.business.exception.MissionMemberStateNotFoundException;
import org.son.haruharu.domain.mission.business.exception.RoomMissionNotOpenException;
import org.son.haruharu.domain.mission.implement.MissionMemberStateWriter;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class MissionMemberStateService {

    private final RoomMissionReader roomMissionReader;
    private final RoomMemberReader roomMemberReader;
    private final MissionMemberStateInitializer initializer;
    private final MissionMemberStateWriter stateWriter;
    private final Clock clock;

    public MissionMemberStateService(
            RoomMissionReader roomMissionReader,
            RoomMemberReader roomMemberReader,
            MissionMemberStateInitializer initializer,
            MissionMemberStateWriter stateWriter,
            Clock clock) {
        this.roomMissionReader = roomMissionReader;
        this.roomMemberReader = roomMemberReader;
        this.initializer = initializer;
        this.stateWriter = stateWriter;
        this.clock = clock;
    }

    @Transactional
    public void impress(Long roomId, Long roomMissionId, Long userId) {
        MutationTarget target = requireOpenTarget(roomId, roomMissionId, userId);
        initializer.initializeTarget(target.mission(), target.member());
        if (stateWriter.impress(roomMissionId, target.member().id(), target.now()) == 0) {
            throw new MissionMemberStateNotFoundException();
        }
    }

    private MutationTarget requireOpenTarget(Long roomId, Long roomMissionId, Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        RoomMember member = roomMemberReader.getByRoomIdAndUserId(roomId, userId);
        RoomMission mission = roomMissionReader.getById(roomMissionId);
        if (!mission.roomId().equals(roomId) || !mission.isOpenAt(now)) {
            throw new RoomMissionNotOpenException();
        }
        return new MutationTarget(mission, member, now);
    }

    private record MutationTarget(RoomMission mission, RoomMember member, LocalDateTime now) {}
}

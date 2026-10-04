package org.son.haruharu.domain.mission.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.business.command.OperatorMissionCommand;
import org.son.haruharu.domain.mission.implement.*;
import org.son.haruharu.domain.mission.implement.exception.OperatorMissionStateConflictException;
import org.son.haruharu.domain.mission.storage.exception.OperatorMissionNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OperatorMissionService {
    private final OperatorMissionReader reader;
    private final OperatorMissionWriter writer;
    private final RoomReader roomReader;
    private final Clock clock;
    private final MissionImageReader images;

    @Transactional
    public OperatorMission create(Long roomId, Long adminId, OperatorMissionCommand command) {
        Room room = roomReader.findByIdForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
        var existing = reader.findByRequestKey(roomId, command.requestKey());
        if (existing.isPresent()) {
            OperatorMission duplicate = existing.orElseThrow(OperatorMissionNotFoundException::new);
            if (!duplicate.matches(command)) throw new OperatorMissionStateConflictException();
            return duplicate;
        }
        images.requireRoom(command.imageId(), roomId);
        LocalDateTime now = LocalDateTime.now(clock);
        OperatorMissionSchedule.validate(room, command.scheduledAt(), now);
        return writer.save(OperatorMission.create(roomId, adminId, command, now));
    }

    @Transactional
    public OperatorMission update(Long roomId, Long missionId, OperatorMissionCommand command) {
        OperatorMission mission = owned(roomId, missionId);
        OperatorMission updated = mission.edited(command);
        images.requireRoom(command.imageId(), roomId);
        Room room = roomReader.findByIdForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
        OperatorMissionSchedule.validate(room, command.scheduledAt(), LocalDateTime.now(clock));
        return writer.save(updated);
    }

    @Transactional
    public OperatorMission cancel(Long roomId, Long missionId) {
        return writer.save(owned(roomId, missionId).cancelled());
    }

    @Transactional
    public OperatorMission delete(Long roomId, Long missionId) {
        return writer.save(owned(roomId, missionId).requestDelete(LocalDateTime.now(clock)));
    }

    public List<OperatorMission> history(Long roomId, Long cursor, int limit) {
        return reader.history(roomId, cursor, Math.min(Math.max(limit, 1), 100));
    }

    private OperatorMission owned(Long roomId, Long missionId) {
        OperatorMission mission = reader.getForUpdate(missionId);
        if (!mission.roomId().equals(roomId)) throw new OperatorMissionNotFoundException();
        return mission;
    }
}

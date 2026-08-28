package org.son.haruharu.domain.mission.business;

import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.domain.mission.business.command.CreateRoomMissionSlotCommand;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotSchedule;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotWriter;
import org.son.haruharu.domain.mission.implement.exception.InvalidRoomMissionSlotScheduleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomMissionSlotService {

    private final RoomMissionSlotWriter roomMissionSlotWriter;

    public RoomMissionSlotService(RoomMissionSlotWriter roomMissionSlotWriter) {
        this.roomMissionSlotWriter = roomMissionSlotWriter;
    }

    @Transactional
    public List<RoomMissionSlot> createForRoom(Long roomId, int totalDays, LocalDateTime startsAt,
                                                LocalDateTime finalSelectionOpensAt,
                                                List<CreateRoomMissionSlotCommand> commands) {
        if (commands == null || commands.stream().anyMatch(command -> command == null)) {
            throw new InvalidRoomMissionSlotScheduleException();
        }
        List<RoomMissionSlot> slots = commands.stream()
                .map(command -> RoomMissionSlot.of(roomId, command.dayNumber(), command.period(),
                        command.opensAt(), command.closesAt()))
                .toList();
        return roomMissionSlotWriter.saveAll(
                RoomMissionSlotSchedule.validate(slots, totalDays, startsAt, finalSelectionOpensAt));
    }

    @Transactional
    public List<RoomMissionSlot> createDefaultsForRoom(Long roomId, int totalDays, LocalDateTime startsAt,
                                                        LocalDateTime finalSelectionOpensAt) {
        return roomMissionSlotWriter.saveAll(
                RoomMissionSlotSchedule.defaults(roomId, totalDays, startsAt, finalSelectionOpensAt));
    }
}

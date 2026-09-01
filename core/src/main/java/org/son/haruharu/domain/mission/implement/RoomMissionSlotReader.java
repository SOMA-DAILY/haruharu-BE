package org.son.haruharu.domain.mission.implement;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

@Component
public class RoomMissionSlotReader {

    private final RoomMissionSlotRepository roomMissionSlotRepository;

    public RoomMissionSlotReader(RoomMissionSlotRepository roomMissionSlotRepository) {
        this.roomMissionSlotRepository = roomMissionSlotRepository;
    }

    public List<RoomMissionSlot> getByRoomId(Long roomId) {
        return roomMissionSlotRepository.findByRoomId(roomId);
    }

    public List<RoomMissionSlot> getDueUnassigned(
            LocalDateTime now, LocalDateTime assignmentCutoff, int limit) {
        return roomMissionSlotRepository.findDueUnassigned(now, assignmentCutoff, limit);
    }

    public List<RoomMissionSlot> getExpiredUnassigned(LocalDateTime now, int limit) {
        return roomMissionSlotRepository.findExpiredUnassigned(now, limit);
    }
}

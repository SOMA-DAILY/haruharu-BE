package org.son.haruharu.domain.mission.implement;

import java.util.List;

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
}

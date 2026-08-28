package org.son.haruharu.domain.mission.implement;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class RoomMissionSlotWriter {

    private final RoomMissionSlotRepository roomMissionSlotRepository;

    public RoomMissionSlotWriter(RoomMissionSlotRepository roomMissionSlotRepository) {
        this.roomMissionSlotRepository = roomMissionSlotRepository;
    }

    public List<RoomMissionSlot> saveAll(List<RoomMissionSlot> slots) {
        return roomMissionSlotRepository.saveAll(slots);
    }
}

package org.son.haruharu.domain.mission.implement;

import java.util.List;

public interface RoomMissionSlotRepository {

    List<RoomMissionSlot> saveAll(List<RoomMissionSlot> slots);

    List<RoomMissionSlot> findByRoomId(Long roomId);
}

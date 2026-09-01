package org.son.haruharu.domain.mission.implement;

import java.util.List;
import java.time.LocalDateTime;

public interface RoomMissionSlotRepository {

    List<RoomMissionSlot> saveAll(List<RoomMissionSlot> slots);

    List<RoomMissionSlot> findByRoomId(Long roomId);

    List<RoomMissionSlot> findDueUnassigned(LocalDateTime now, LocalDateTime assignmentCutoff, int limit);

    List<RoomMissionSlot> findExpiredUnassigned(LocalDateTime now, int limit);
}

package org.son.haruharu.domain.mission.common.implement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomMissionRepository {
    RoomMission save(RoomMission mission);
    Optional<RoomMission> findById(Long id);
    Optional<RoomMission> findByIdForUpdate(Long id);
    List<RoomMission> findPage(Long roomId, Long afterId, int limit);
    List<RoomMission> findByIdsInRoom(Long roomId, Collection<Long> ids);
    List<RoomMission> findByIds(Collection<Long> ids);
    List<RoomMission> findByRoomIdAndType(Long roomId, MissionType type);
    List<RoomMission> findVisiblePage(Long roomId, Long afterId, java.time.LocalDateTime now, int limit);
}

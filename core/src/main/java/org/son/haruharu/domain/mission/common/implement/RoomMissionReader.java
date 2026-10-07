package org.son.haruharu.domain.mission.common.implement;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.common.storage.exception.MissionNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomMissionReader {
    private final RoomMissionRepository repository;

    public RoomMission getById(Long id) {
        return repository.findById(id).orElseThrow(MissionNotFoundException::new);
    }

    public RoomMission getInRoom(Long roomId, Long id) {
        RoomMission mission = getById(id);
        if (!mission.roomId().equals(roomId)) throw new MissionNotFoundException();
        return mission;
    }

    public RoomMission getForUpdate(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(MissionNotFoundException::new);
    }

    public RoomMission getForUpdateInRoom(Long roomId, Long id) {
        RoomMission mission = getForUpdate(id);
        if (!mission.roomId().equals(roomId)) throw new MissionNotFoundException();
        return mission;
    }

    public RoomMission requireVisible(RoomMission mission, org.son.haruharu.domain.room.implement.Room room,
                                      java.time.LocalDateTime now) {
        if (!mission.visibleAt(now, room)) throw new MissionNotFoundException();
        return mission;
    }

    public List<RoomMission> page(Long roomId, Long afterId, int limit) {
        return repository.findPage(roomId, afterId, limit);
    }

    public List<RoomMission> findByIdsInRoom(Long roomId, Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : repository.findByIdsInRoom(roomId, ids);
    }

    public List<RoomMission> findByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : repository.findByIds(ids);
    }

    public List<RoomMission> visiblePage(Long roomId, Long afterId, java.time.LocalDateTime now, int limit) {
        return repository.findVisiblePage(roomId, afterId, now, limit);
    }

}

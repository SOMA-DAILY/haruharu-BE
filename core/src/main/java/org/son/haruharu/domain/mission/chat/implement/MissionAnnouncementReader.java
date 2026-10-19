package org.son.haruharu.domain.mission.chat.implement;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.chat.storage.exception.MissionAnnouncementNotFoundException;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MissionAnnouncementReader {
    private final MissionAnnouncementRepository repository;

    public MissionAnnouncement getById(Long id) {
        return repository.findById(id).orElseThrow(MissionAnnouncementNotFoundException::new);
    }

    public MissionAnnouncement getInRoom(Long roomId, Long id) {
        MissionAnnouncement announcement = getById(id);
        if (!announcement.roomId().equals(roomId)) throw new MissionAnnouncementNotFoundException();
        return announcement;
    }

    public MissionAnnouncement getForUpdate(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(MissionAnnouncementNotFoundException::new);
    }

    public MissionAnnouncement getForUpdateInMission(RoomMission mission, Long id) {
        MissionAnnouncement item = getForUpdate(id);
        if (!item.roomMissionId().equals(mission.id()) || !item.roomId().equals(mission.roomId()))
            throw new MissionAnnouncementNotFoundException();
        return item;
    }

    public List<MissionAnnouncement> page(Long missionId, Long after, int limit) {
        return repository.findPage(missionId, after, limit);
    }

    public List<MissionAnnouncement> findByRoomId(Long roomId) {
        return repository.findByRoomId(roomId);
    }

    public List<MissionAnnouncement> findByMissionId(Long missionId) {
        return repository.findByMissionId(missionId);
    }

    public List<MissionAnnouncement> findByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : repository.findByIds(ids);
    }

    public List<MissionAnnouncement> findDue(LocalDateTime now, int limit) {
        return repository.findDue(now, limit);
    }

    public List<MissionAnnouncement> findSendingSince(LocalDateTime threshold, int limit) {
        return repository.findSendingSince(threshold, limit);
    }
}

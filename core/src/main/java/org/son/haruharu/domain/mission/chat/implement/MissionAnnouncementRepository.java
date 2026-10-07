package org.son.haruharu.domain.mission.chat.implement;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MissionAnnouncementRepository {
    List<MissionAnnouncement> findPage(Long missionId, Long after, int limit);
    MissionAnnouncement save(MissionAnnouncement announcement);
    Optional<MissionAnnouncement> findById(Long id);
    Optional<MissionAnnouncement> findByIdForUpdate(Long id);
    List<MissionAnnouncement> findByRoomId(Long roomId);
    List<MissionAnnouncement> findByMissionId(Long missionId);
    List<MissionAnnouncement> findByIds(Collection<Long> ids);
    List<MissionAnnouncement> findDue(LocalDateTime now, int limit);
    List<MissionAnnouncement> findSendingSince(LocalDateTime threshold, int limit);
    int claim(Long id, LocalDateTime now);
    int markSent(Long id, Long messageId, LocalDateTime now);
    int markFailed(Long id, MissionAnnouncementStatus from, String reason, LocalDateTime now);
    int markFailedIfUnchanged(MissionAnnouncement snapshot, String reason, LocalDateTime now);
    int updateIfScheduled(Long id, LocalDateTime runAt, String content, boolean push,
                          String pushTitle, String pushBody, String actor, LocalDateTime now);
    int cancelIfScheduled(Long id, String actor, LocalDateTime now);
    int cancelScheduledForMission(Long missionId, String actor, LocalDateTime now);
}

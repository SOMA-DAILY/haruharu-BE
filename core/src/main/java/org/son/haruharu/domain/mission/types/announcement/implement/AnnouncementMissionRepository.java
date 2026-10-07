package org.son.haruharu.domain.mission.types.announcement.implement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnnouncementMissionRepository {
    AnnouncementMission save(AnnouncementMission mission);
    Optional<AnnouncementMission> findByMissionId(Long missionId);
    List<AnnouncementMission> findByMissionIds(Collection<Long> missionIds);
    List<AnnouncementRevealPlan> findRevealPlans(Long roomId);
}

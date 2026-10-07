package org.son.haruharu.domain.mission.types.announcement.implement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnnouncementMissionReader {
    private final AnnouncementMissionRepository repository;

    public Optional<AnnouncementMission> findByMissionId(Long missionId) {
        return repository.findByMissionId(missionId);
    }
    public AnnouncementMission getByMissionId(Long missionId) {
        return findByMissionId(missionId).orElseThrow(() ->
                new IllegalStateException("Missing announcement mission definition: missionId=" + missionId));
    }
    public List<AnnouncementMission> findByMissionIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : repository.findByMissionIds(ids);
    }
    public List<AnnouncementRevealPlan> findRevealPlans(Long roomId) { return repository.findRevealPlans(roomId); }
}

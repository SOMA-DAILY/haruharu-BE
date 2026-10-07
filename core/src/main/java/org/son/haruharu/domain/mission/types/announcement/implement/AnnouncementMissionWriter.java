package org.son.haruharu.domain.mission.types.announcement.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnnouncementMissionWriter {
    private final AnnouncementMissionRepository repository;
    public AnnouncementMission save(AnnouncementMission mission) { return repository.save(mission); }
}

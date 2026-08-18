package org.son.haruharu.admin.domain.mission.implement;

import org.son.haruharu.domain.mission.implement.Mission;
import org.springframework.stereotype.Component;

@Component
public class AdminMissionWriter {

    private final AdminMissionRepository adminMissionRepository;

    public AdminMissionWriter(AdminMissionRepository adminMissionRepository) {
        this.adminMissionRepository = adminMissionRepository;
    }

    public Mission save(Mission mission) {
        return adminMissionRepository.save(mission);
    }
}

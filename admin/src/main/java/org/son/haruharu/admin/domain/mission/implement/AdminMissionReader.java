package org.son.haruharu.admin.domain.mission.implement;

import org.son.haruharu.admin.domain.mission.storage.exception.AdminMissionNotFoundException;
import org.son.haruharu.domain.mission.implement.Mission;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminMissionReader {

    private final AdminMissionRepository adminMissionRepository;

    public AdminMissionReader(AdminMissionRepository adminMissionRepository) {
        this.adminMissionRepository = adminMissionRepository;
    }

    public List<Mission> findAll() {
        return adminMissionRepository.findAll();
    }

    public Mission getById(Long id) {
        return adminMissionRepository.findById(id)
                .orElseThrow(AdminMissionNotFoundException::new);
    }

    public boolean existsByDescription(String description) {
        return adminMissionRepository.existsByDescription(description);
    }
}

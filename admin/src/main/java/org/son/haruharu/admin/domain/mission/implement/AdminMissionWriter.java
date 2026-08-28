package org.son.haruharu.admin.domain.mission.implement;

import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;
import org.springframework.stereotype.Component;

@Component
public class AdminMissionWriter {

    private final AdminMissionRepository adminMissionRepository;

    public AdminMissionWriter(AdminMissionRepository adminMissionRepository) {
        this.adminMissionRepository = adminMissionRepository;
    }

    public Mission saveMission(Mission mission) {
        return adminMissionRepository.saveMission(mission);
    }

    public MissionRevision saveRevision(MissionRevision revision) {
        return adminMissionRepository.saveRevision(revision);
    }

    public java.util.List<MissionRevisionEligibility> saveEligibilities(
            java.util.List<MissionRevisionEligibility> eligibilities
    ) {
        return adminMissionRepository.saveEligibilities(eligibilities);
    }
}

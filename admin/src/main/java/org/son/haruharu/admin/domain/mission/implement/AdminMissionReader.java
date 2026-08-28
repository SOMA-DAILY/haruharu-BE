package org.son.haruharu.admin.domain.mission.implement;

import org.son.haruharu.admin.domain.mission.storage.exception.AdminMissionNotFoundException;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class AdminMissionReader {

    private final AdminMissionRepository adminMissionRepository;

    public AdminMissionReader(AdminMissionRepository adminMissionRepository) {
        this.adminMissionRepository = adminMissionRepository;
    }

    public List<Mission> findAll(int offset, int limit) {
        return adminMissionRepository.findAll(offset, limit);
    }

    public long countMissions() {
        return adminMissionRepository.countMissions();
    }

    public Mission getById(Long id) {
        return adminMissionRepository.findById(id)
                .orElseThrow(AdminMissionNotFoundException::new);
    }

    public Mission getByIdForUpdate(Long id) {
        return adminMissionRepository.findByIdForUpdate(id)
                .orElseThrow(AdminMissionNotFoundException::new);
    }

    public List<MissionRevision> findRevisions(Long missionId, int offset, int limit) {
        return adminMissionRepository.findRevisionsByMissionId(missionId, offset, limit);
    }

    public long countRevisions(Long missionId) {
        return adminMissionRepository.countRevisionsByMissionId(missionId);
    }

    public MissionRevision getLatestRevision(Long missionId) {
        return adminMissionRepository.findLatestRevisionByMissionId(missionId)
                .orElseThrow(AdminMissionNotFoundException::new);
    }

    public List<MissionRevision> findRevisionsByIds(Set<Long> revisionIds) {
        return adminMissionRepository.findRevisionsByIds(revisionIds);
    }

    public MissionRevision getRevision(Long revisionId) {
        return adminMissionRepository.findRevisionById(revisionId)
                .orElseThrow(AdminMissionNotFoundException::new);
    }

    public List<MissionRevisionEligibility> findEligibilities(Long revisionId) {
        return adminMissionRepository.findEligibilitiesByRevisionId(revisionId);
    }

    public List<MissionRevisionEligibility> findEligibilitiesByRevisionIds(Set<Long> revisionIds) {
        return adminMissionRepository.findEligibilitiesByRevisionIds(revisionIds);
    }
}

package org.son.haruharu.admin.domain.mission.implement;

import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 어드민 미션 카탈로그 포트(도메인 계약). storage가 JPA로 구현한다. Entity 비의존. */
public interface AdminMissionRepository {
    List<Mission> findAll(int offset, int limit);
    long countMissions();
    Optional<Mission> findById(Long id);
    Optional<Mission> findByIdForUpdate(Long id);
    List<MissionRevision> findRevisionsByMissionId(Long missionId, int offset, int limit);
    long countRevisionsByMissionId(Long missionId);
    Optional<MissionRevision> findLatestRevisionByMissionId(Long missionId);
    List<MissionRevision> findRevisionsByIds(Set<Long> revisionIds);
    Optional<MissionRevision> findRevisionById(Long revisionId);
    List<MissionRevisionEligibility> findEligibilitiesByRevisionId(Long revisionId);
    List<MissionRevisionEligibility> findEligibilitiesByRevisionIds(Set<Long> revisionIds);
    Mission saveMission(Mission mission);
    MissionRevision saveRevision(MissionRevision revision);
    List<MissionRevisionEligibility> saveEligibilities(List<MissionRevisionEligibility> eligibilities);
}

package org.son.haruharu.admin.domain.mission.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionRepository;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;
import org.son.haruharu.domain.mission.storage.MissionEntity;
import org.son.haruharu.domain.mission.storage.MissionRevisionEligibilityEntity;
import org.son.haruharu.domain.mission.storage.MissionRevisionEligibilityJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionRevisionEntity;
import org.son.haruharu.domain.mission.storage.MissionRevisionJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 어드민 미션 카탈로그 포트의 JPA 어댑터. Entity↔Domain 변환을 전담한다. */
@Repository
@RequiredArgsConstructor
public class AdminMissionRepositoryImpl implements AdminMissionRepository {

    private final AdminMissionJpaRepository jpaRepository;
    private final MissionRevisionJpaRepository revisionJpaRepository;
    private final MissionRevisionEligibilityJpaRepository eligibilityJpaRepository;

    @Override
    public List<Mission> findAll(int offset, int limit) {
        return jpaRepository.findAll(PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "id")))
                .stream().map(MissionEntity::toDomain).toList();
    }

    @Override
    public long countMissions() {
        return jpaRepository.count();
    }

    @Override
    public Optional<Mission> findById(Long id) {
        return jpaRepository.findById(id).map(MissionEntity::toDomain);
    }

    @Override
    public Optional<Mission> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id).map(MissionEntity::toDomain);
    }

    @Override
    public List<MissionRevision> findRevisionsByMissionId(Long missionId, int offset, int limit) {
        return revisionJpaRepository.findByMissionIdOrderByRevisionNumberDesc(
                        missionId, PageRequest.of(offset / limit, limit)).stream()
                .map(MissionRevisionEntity::toDomain)
                .toList();
    }

    @Override
    public long countRevisionsByMissionId(Long missionId) {
        return revisionJpaRepository.countByMissionId(missionId);
    }

    @Override
    public Optional<MissionRevision> findLatestRevisionByMissionId(Long missionId) {
        return revisionJpaRepository.findTopByMissionIdOrderByRevisionNumberDesc(missionId)
                .map(MissionRevisionEntity::toDomain);
    }

    @Override
    public List<MissionRevision> findRevisionsByIds(Set<Long> revisionIds) {
        return revisionJpaRepository.findByIdIn(revisionIds).stream()
                .map(MissionRevisionEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<MissionRevision> findRevisionById(Long revisionId) {
        return revisionJpaRepository.findById(revisionId).map(MissionRevisionEntity::toDomain);
    }

    @Override
    public List<MissionRevisionEligibility> findEligibilitiesByRevisionId(Long revisionId) {
        return eligibilityJpaRepository.findByMissionRevisionIdOrderByDayNumberAscPeriodAsc(revisionId).stream()
                .map(MissionRevisionEligibilityEntity::toDomain)
                .toList();
    }

    @Override
    public List<MissionRevisionEligibility> findEligibilitiesByRevisionIds(Set<Long> revisionIds) {
        return eligibilityJpaRepository.findByMissionRevisionIdIn(revisionIds).stream()
                .map(MissionRevisionEligibilityEntity::toDomain)
                .toList();
    }

    @Override
    public Mission saveMission(Mission mission) {
        return jpaRepository.save(MissionEntity.from(mission)).toDomain();
    }

    @Override
    public MissionRevision saveRevision(MissionRevision revision) {
        return revisionJpaRepository.save(MissionRevisionEntity.from(revision)).toDomain();
    }

    @Override
    public List<MissionRevisionEligibility> saveEligibilities(
            List<MissionRevisionEligibility> eligibilities
    ) {
        return eligibilityJpaRepository.saveAll(eligibilities.stream()
                        .map(MissionRevisionEligibilityEntity::from).toList()).stream()
                .map(MissionRevisionEligibilityEntity::toDomain)
                .toList();
    }
}

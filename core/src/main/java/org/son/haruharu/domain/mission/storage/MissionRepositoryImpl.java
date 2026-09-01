package org.son.haruharu.domain.mission.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionCatalogEntry;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;
import org.son.haruharu.domain.mission.implement.MissionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MissionRepositoryImpl implements MissionRepository {

    private final MissionRevisionJpaRepository revisionJpaRepository;
    private final MissionRevisionEligibilityJpaRepository eligibilityJpaRepository;

    @Override
    public List<MissionCatalogEntry> findActiveCatalog() {
        List<MissionRevision> revisions = revisionJpaRepository.findActiveCurrentCatalog().stream()
                .map(MissionRevisionEntity::toDomain).toList();
        if (revisions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MissionRevisionEligibility>> eligibilitiesByRevision =
                eligibilityJpaRepository.findByMissionRevisionIdIn(
                                revisions.stream().map(MissionRevision::id).toList()).stream()
                        .map(MissionRevisionEligibilityEntity::toDomain)
                        .collect(Collectors.groupingBy(MissionRevisionEligibility::missionRevisionId));
        return revisions.stream()
                .map(revision -> new MissionCatalogEntry(
                        revision, eligibilitiesByRevision.getOrDefault(revision.id(), List.of())))
                .toList();
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
}

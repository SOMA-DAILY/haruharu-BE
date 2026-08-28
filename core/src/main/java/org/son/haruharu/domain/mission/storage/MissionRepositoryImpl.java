package org.son.haruharu.domain.mission.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class MissionRepositoryImpl implements MissionRepository {

    private final MissionRevisionJpaRepository revisionJpaRepository;

    @Override
    public List<MissionRevision> findActiveRevisions() {
        return revisionJpaRepository.findActiveCurrentRevisions().stream()
                .map(MissionRevisionEntity::toDomain)
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

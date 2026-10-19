package org.son.haruharu.domain.cohort.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortRepository;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CohortRepositoryImpl implements CohortRepository {

    private final CohortJpaRepository jpaRepository;

    @Override
    public Optional<Cohort> findById(Long id) {
        return jpaRepository.findById(id).map(CohortEntity::toDomain);
    }

    @Override
    public Optional<Cohort> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id).map(CohortEntity::toDomain);
    }

    @Override
    public Optional<Cohort> findByIdForShare(Long id) {
        return jpaRepository.findByIdForShare(id).map(CohortEntity::toDomain);
    }

    @Override
    public Optional<Long> findTrackIdById(Long id) {
        return jpaRepository.findTrackIdById(id);
    }

    @Override
    public List<Cohort> findAll(Long trackId, CohortStatus status) {
        return jpaRepository.findAllFiltered(trackId, status).stream().map(CohortEntity::toDomain).toList();
    }

    @Override
    public List<Cohort> findAllByStatus(CohortStatus status) {
        return jpaRepository.findAllByStatus(status).stream().map(CohortEntity::toDomain).toList();
    }

    @Override
    public boolean existsByTrackIdAndSequence(Long trackId, int sequence) {
        return jpaRepository.existsByTrackIdAndSequence(trackId, sequence);
    }

    @Override
    public boolean existsByTrackIdAndStatus(Long trackId, CohortStatus status) {
        return jpaRepository.existsByTrackIdAndStatus(trackId, status);
    }

    @Override
    public Cohort save(Cohort cohort) {
        return jpaRepository.saveAndFlush(CohortEntity.from(cohort)).toDomain();
    }
}

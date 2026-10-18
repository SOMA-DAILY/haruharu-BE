package org.son.haruharu.domain.cohort.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RecruitTrackRepositoryImpl implements RecruitTrackRepository {

    private final RecruitTrackJpaRepository jpaRepository;

    @Override
    public Optional<RecruitTrack> findById(Long id) {
        return jpaRepository.findById(id).map(RecruitTrackEntity::toDomain);
    }

    @Override
    public Optional<RecruitTrack> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id).map(RecruitTrackEntity::toDomain);
    }

    @Override
    public List<RecruitTrack> findAll() {
        return jpaRepository.findAllByOrderByDisplayOrderAscIdAsc().stream().map(RecruitTrackEntity::toDomain).toList();
    }

    @Override
    public List<RecruitTrack> findAllByIdIn(List<Long> ids) {
        return jpaRepository.findAllByIdIn(ids).stream().map(RecruitTrackEntity::toDomain).toList();
    }

    @Override
    public boolean existsByKey(String key) {
        return jpaRepository.existsByKey(key);
    }

    @Override
    public RecruitTrack save(RecruitTrack track) {
        return jpaRepository.saveAndFlush(RecruitTrackEntity.from(track)).toDomain();
    }
}

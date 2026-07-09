package org.son.haruharu.domain.matching.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueRepository;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MatchingQueueRepositoryImpl implements MatchingQueueRepository {

    private final MatchingQueueJpaRepository jpaRepository;

    @Override
    public boolean existsByUserIdAndStatus(Long userId, MatchingQueueStatus status) {
        return jpaRepository.existsByUserIdAndStatus(userId, status);
    }

    @Override
    public Optional<MatchingQueue> findByUserIdAndStatus(Long userId, MatchingQueueStatus status) {
        return jpaRepository.findByUserIdAndStatus(userId, status).map(MatchingQueueEntity::toDomain);
    }

    @Override
    public List<MatchingQueue> findAllByStatusOrderByQueuedAt(MatchingQueueStatus status) {
        return jpaRepository.findAllByStatusOrderByQueuedAtAsc(status).stream()
                .map(MatchingQueueEntity::toDomain)
                .toList();
    }

    @Override
    public MatchingQueue save(MatchingQueue queue) {
        return jpaRepository.save(MatchingQueueEntity.from(queue)).toDomain();
    }

    @Override
    public boolean transitionStatus(Long id, MatchingQueueStatus from, MatchingQueueStatus to, LocalDateTime now) {
        return jpaRepository.transitionStatus(id, from, to, now) == 1;
    }
}

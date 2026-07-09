package org.son.haruharu.domain.matching.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchingQueueRepository {

    boolean existsByUserIdAndStatus(Long userId, MatchingQueueStatus status);

    Optional<MatchingQueue> findByUserIdAndStatus(Long userId, MatchingQueueStatus status);

    List<MatchingQueue> findAllByStatusOrderByQueuedAt(MatchingQueueStatus status);

    MatchingQueue save(MatchingQueue queue);

    boolean transitionStatus(Long id, MatchingQueueStatus from, MatchingQueueStatus to, LocalDateTime now);
}

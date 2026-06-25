package org.son.haruharu.domain.matching.implement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchingQueueRepository {

    boolean existsByUserIdAndStatus(UUID userId, MatchingQueueStatus status);

    Optional<MatchingQueue> findByUserIdAndStatus(UUID userId, MatchingQueueStatus status);

    List<MatchingQueue> findAllByStatusOrderByQueuedAt(MatchingQueueStatus status);

    MatchingQueue save(MatchingQueue queue);
}

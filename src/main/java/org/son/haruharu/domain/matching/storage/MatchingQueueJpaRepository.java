package org.son.haruharu.domain.matching.storage;

import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchingQueueJpaRepository extends JpaRepository<MatchingQueueEntity, UUID> {

    boolean existsByUserIdAndStatus(UUID userId, MatchingQueueStatus status);

    Optional<MatchingQueueEntity> findByUserIdAndStatus(UUID userId, MatchingQueueStatus status);

    List<MatchingQueueEntity> findAllByStatusOrderByQueuedAtAsc(MatchingQueueStatus status);
}

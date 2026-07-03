package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VideoReactionJpaRepository extends JpaRepository<VideoReactionEntity, UUID> {

    Optional<VideoReactionEntity> findByVideoPostIdAndUserId(UUID videoPostId, Long userId);
}

package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoReactionJpaRepository extends JpaRepository<VideoReactionEntity, Long> {

    Optional<VideoReactionEntity> findByVideoPostIdAndUserId(Long videoPostId, Long userId);
}

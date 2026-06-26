package org.son.haruharu.domain.video.implement;

import java.util.Optional;
import java.util.UUID;

public interface VideoReactionRepository {

    Optional<VideoReaction> findByVideoPostIdAndUserId(UUID videoPostId, UUID userId);

    VideoReaction save(VideoReaction reaction);

    void deleteById(UUID id);
}

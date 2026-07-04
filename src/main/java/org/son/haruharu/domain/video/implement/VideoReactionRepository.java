package org.son.haruharu.domain.video.implement;

import java.util.Optional;

public interface VideoReactionRepository {

    Optional<VideoReaction> findByVideoPostIdAndUserId(Long videoPostId, Long userId);

    VideoReaction save(VideoReaction reaction);

    void deleteById(Long id);
}

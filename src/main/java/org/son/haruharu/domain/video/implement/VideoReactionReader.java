package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VideoReactionReader {

    private final VideoReactionRepository videoReactionRepository;

    public Optional<VideoReaction> findMine(UUID videoPostId, UUID userId) {
        return videoReactionRepository.findByVideoPostIdAndUserId(videoPostId, userId);
    }
}

package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VideoReactionReader {

    private final VideoReactionRepository videoReactionRepository;

    public Optional<VideoReaction> findMine(Long videoPostId, Long userId) {
        return videoReactionRepository.findByVideoPostIdAndUserId(videoPostId, userId);
    }
}

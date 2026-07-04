package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoReactionWriter {

    private final VideoReactionRepository videoReactionRepository;

    public VideoReaction save(VideoReaction reaction) {
        return videoReactionRepository.save(reaction);
    }

    public void delete(Long id) {
        videoReactionRepository.deleteById(id);
    }
}

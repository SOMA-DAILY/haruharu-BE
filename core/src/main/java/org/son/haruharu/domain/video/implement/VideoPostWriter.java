package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoPostWriter {

    private final VideoPostRepository videoPostRepository;

    public VideoPost save(VideoPost post) {
        return videoPostRepository.save(post);
    }
}

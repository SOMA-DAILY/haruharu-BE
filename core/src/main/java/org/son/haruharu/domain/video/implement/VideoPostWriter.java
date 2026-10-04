package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;

@Component
@RequiredArgsConstructor
public class VideoPostWriter {

    private final VideoPostRepository videoPostRepository;

    public VideoPost save(VideoPost post) {
        return videoPostRepository.save(post);
    }

    public void updateCaption(Long videoPostId, String caption) {
        if (!videoPostRepository.updateCaption(videoPostId, caption)) {
            throw new VideoPostNotFoundException();
        }
    }
}

package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoPostWriter {

    private final VideoPostRepository videoPostRepository;

    public VideoPost save(VideoPost post) {
        return videoPostRepository.save(post);
    }

    /** 조회 이후 삭제·숨김된 영상이면 쓰지 않고 없는 영상으로 처리한다. */
    public void updateCaption(Long videoPostId, String caption) {
        if (!videoPostRepository.updateCaption(videoPostId, caption)) {
            throw new VideoPostNotFoundException();
        }
    }
}

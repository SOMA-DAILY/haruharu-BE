package org.son.haruharu.domain.video.implement;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoPostWriter {

    private final VideoPostRepository videoPostRepository;

    public VideoPost save(VideoPost post) {
        return videoPostRepository.save(post);
    }

    /** 데모 리셋 전용 하드삭제(#267). 지정 멤버들의 영상을 물리 삭제한다. */
    public void deleteByRoomMemberIds(List<Long> roomMemberIds) {
        videoPostRepository.deleteByRoomMemberIds(roomMemberIds);
    }
}

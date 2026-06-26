package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VideoPostReader {

    private final VideoPostRepository videoPostRepository;

    /** 슬롯이 비어 있으면(미시작 Day 등) 0. video_posts가 비어 있는 #39 시점엔 항상 0. */
    public long countUploaded(UUID roomMemberId, List<UUID> slotIds) {
        if (slotIds.isEmpty()) {
            return 0;
        }
        return videoPostRepository.countUploaded(roomMemberId, slotIds);
    }
}

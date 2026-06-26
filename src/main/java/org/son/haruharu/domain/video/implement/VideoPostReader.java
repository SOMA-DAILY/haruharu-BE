package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VideoPostReader {

    private final VideoPostRepository videoPostRepository;

    public VideoPost getById(UUID id) {
        return videoPostRepository.findById(id)
                .orElseThrow(VideoPostNotFoundException::new);
    }

    /** 슬롯이 비어 있으면(미시작 Day 등) 0. */
    public long countUploaded(UUID roomMemberId, List<UUID> slotIds) {
        if (slotIds.isEmpty()) {
            return 0;
        }
        return videoPostRepository.countUploaded(roomMemberId, slotIds);
    }

    public boolean hasActiveUpload(UUID roomMemberId, UUID uploadSlotId) {
        return videoPostRepository.existsActiveBySlot(roomMemberId, uploadSlotId);
    }

    public boolean hasEverUploadedMission(UUID roomMemberId, UUID uploadSlotId) {
        return videoPostRepository.existsMissionUploadBySlot(roomMemberId, uploadSlotId);
    }

    public List<VideoPost> getFeed(List<UUID> slotIds) {
        return videoPostRepository.findFeed(slotIds);
    }
}

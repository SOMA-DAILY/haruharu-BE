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

    public long countUploaded(UUID roomMemberId, int dayNumber) {
        return videoPostRepository.countUploaded(roomMemberId, dayNumber);
    }

    public boolean hasActiveUpload(UUID roomMemberId, int dayNumber, int slotIndex) {
        return videoPostRepository.existsActiveBySlot(roomMemberId, dayNumber, slotIndex);
    }

    public boolean hasEverUploadedMission(UUID roomMemberId, int dayNumber, int slotIndex) {
        return videoPostRepository.existsMissionUploadBySlot(roomMemberId, dayNumber, slotIndex);
    }

    public List<VideoPost> getFeed(List<UUID> roomMemberIds, int dayNumber) {
        return videoPostRepository.findFeed(roomMemberIds, dayNumber);
    }
}

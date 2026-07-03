package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VideoPostReader {

    private final VideoPostRepository videoPostRepository;

    public VideoPost getById(Long id) {
        return videoPostRepository.findById(id)
                .orElseThrow(VideoPostNotFoundException::new);
    }

    public long countUploaded(Long roomMemberId, int dayNumber) {
        return videoPostRepository.countUploaded(roomMemberId, dayNumber);
    }

    public boolean hasActiveUpload(Long roomMemberId, int dayNumber, int slotIndex) {
        return videoPostRepository.existsActiveBySlot(roomMemberId, dayNumber, slotIndex);
    }

    // MVP 미션 비활성화 동안 미호출(도메인 유지). 미션 재개 시 VideoService의 슬롯당 1회 열쇠 지급 게이트로 복원.
    public boolean hasEverUploadedMission(Long roomMemberId, int dayNumber, int slotIndex) {
        return videoPostRepository.existsMissionUploadBySlot(roomMemberId, dayNumber, slotIndex);
    }

    public List<VideoPost> getFeed(List<Long> roomMemberIds, int dayNumber) {
        return videoPostRepository.findFeed(roomMemberIds, dayNumber);
    }
}

package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VideoPostReader {

    private final VideoPostRepository videoPostRepository;

    public VideoPost getById(Long id) {
        return videoPostRepository.findById(id)
                .orElseThrow(VideoPostNotFoundException::new);
    }

    /** 주어진 슬롯 창 중 이 멤버가 업로드한 slot_start들. 슬롯별 잠금 판정용 배치 조회. */
    public List<LocalDateTime> getUploadedSlotStarts(Long roomMemberId, List<LocalDateTime> slotStarts) {
        return videoPostRepository.findUploadedSlotStarts(roomMemberId, slotStarts);
    }

    public List<LocalDateTime> getVisibleSlotStarts(List<Long> roomMemberIds, List<LocalDateTime> slotStarts) {
        return videoPostRepository.findVisibleSlotStarts(roomMemberIds, slotStarts);
    }

    public boolean hasActiveUpload(Long roomMemberId, LocalDateTime slotStart) {
        return videoPostRepository.existsActiveBySlotStart(roomMemberId, slotStart);
    }

    public List<VideoPost> getFeed(List<Long> roomMemberIds, List<LocalDateTime> slotStarts) {
        return videoPostRepository.findFeed(roomMemberIds, slotStarts);
    }

    /** 한 멤버의 활성 영상 전체(slot_start 오름차순). 매칭 상대 카드용. */
    public List<VideoPost> findActiveByRoomMember(Long roomMemberId) {
        return videoPostRepository.findActiveByRoomMember(roomMemberId);
    }

    /** 주어진 방의 멤버들 중에 해당 슬롯들에 활성(미삭제) 영상을 올린 멤버 id **/
    public List<Long> getUploadedRoomMemberIds(List<Long> roomMemberIds, List<LocalDateTime> slotStarts) {
        return videoPostRepository.findUploadedRoomMemberIds(roomMemberIds, slotStarts);
    }
}

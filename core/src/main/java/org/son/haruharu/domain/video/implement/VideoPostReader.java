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

    /** 폐기 예정 일일보상 호환용(dayNumber 기준 완주 수). 슬롯 로직엔 쓰지 않는다. */
    public long countUploaded(Long roomMemberId, int dayNumber) {
        return videoPostRepository.countUploaded(roomMemberId, dayNumber);
    }

    /** 주어진 슬롯 창 중 이 멤버가 업로드한 slot_start들. 슬롯별 잠금 판정용 배치 조회. */
    public List<LocalDateTime> getUploadedSlotStarts(Long roomMemberId, List<LocalDateTime> slotStarts) {
        return videoPostRepository.findUploadedSlotStarts(roomMemberId, slotStarts);
    }

    public boolean hasActiveUpload(Long roomMemberId, LocalDateTime slotStart) {
        return videoPostRepository.existsActiveBySlotStart(roomMemberId, slotStart);
    }

    public List<VideoPost> getFeed(List<Long> roomMemberIds, List<LocalDateTime> slotStarts) {
        return videoPostRepository.findFeed(roomMemberIds, slotStarts);
    }

    /** 한 멤버의 활성 영상 전체(Day·슬롯 오름차순). 매칭 상대 카드용. */
    public List<VideoPost> findActiveByRoomMember(Long roomMemberId) {
        return videoPostRepository.findActiveByRoomMember(roomMemberId);
    }
}

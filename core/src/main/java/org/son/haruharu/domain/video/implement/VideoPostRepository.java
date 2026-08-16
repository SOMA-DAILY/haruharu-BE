package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VideoPostRepository {

    VideoPost save(VideoPost post);

    Optional<VideoPost> findById(Long id);

    /** 폐기 예정 일일보상 호환용(dayNumber 기준 완주 수). 슬롯 로직엔 쓰지 않는다. */
    long countUploaded(Long roomMemberId, int dayNumber);

    /** 주어진 슬롯 창 중 이 멤버가 업로드(미삭제)한 slot_start들. 슬롯별 잠금 판정용 배치 조회. */
    List<LocalDateTime> findUploadedSlotStarts(Long roomMemberId, List<LocalDateTime> slotStarts);

    /** 업로드 가능 여부 게이트: 해당 slot_start에 활성(미삭제) 영상이 이미 있는지. 재촬영은 DELETE 선행 필요. */
    boolean existsActiveBySlotStart(Long roomMemberId, LocalDateTime slotStart);

    /** 피드: 그 방 멤버들이 주어진 슬롯 창(slot_start 목록)에 올린 활성·비숨김 영상. */
    List<VideoPost> findFeed(List<Long> roomMemberIds, List<LocalDateTime> slotStarts);

    /** 한 멤버의 활성 영상 전체(slot_start 오름차순). 매칭 상대 카드의 영상용. */
    List<VideoPost> findActiveByRoomMember(Long roomMemberId);
}

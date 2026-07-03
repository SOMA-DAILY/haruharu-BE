package org.son.haruharu.domain.video.implement;

import java.util.List;
import java.util.Optional;

public interface VideoPostRepository {

    VideoPost save(VideoPost post);

    Optional<VideoPost> findById(Long id);

    /** 해당 멤버가 그 Day에 올린(삭제 안 된) 영상 수. /today의 업로드 진행 파생. */
    long countUploaded(Long roomMemberId, int dayNumber);

    /** 업로드 가능 여부 게이트: 슬롯에 활성(미삭제) 영상이 이미 있는지. 재촬영은 DELETE 선행 필요. */
    boolean existsActiveBySlot(Long roomMemberId, int dayNumber, int slotIndex);

    /** 열쇠 지급 멱등 게이트: 슬롯에 미션 영상이 과거에 있었는지(삭제분 포함). 재촬영 farming 방지. */
    boolean existsMissionUploadBySlot(Long roomMemberId, int dayNumber, int slotIndex);

    /** day N 피드: 그 방 멤버들이 그 Day에 올린 활성·비숨김 영상. */
    List<VideoPost> findFeed(List<Long> roomMemberIds, int dayNumber);
}

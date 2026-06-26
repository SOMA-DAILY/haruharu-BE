package org.son.haruharu.domain.video.implement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoPostRepository {

    VideoPost save(VideoPost post);

    Optional<VideoPost> findById(UUID id);

    /** 해당 멤버가 주어진 슬롯들에 올린(삭제 안 된) 영상 수. /today의 업로드 진행 파생. */
    long countUploaded(UUID roomMemberId, List<UUID> slotIds);

    /** 업로드 가능 여부 게이트: 슬롯에 활성(미삭제) 영상이 이미 있는지. 재촬영은 DELETE 선행 필요. */
    boolean existsActiveBySlot(UUID roomMemberId, UUID uploadSlotId);

    /** 열쇠 지급 멱등 게이트: 슬롯에 미션 영상이 과거에 있었는지(삭제분 포함). 재촬영 farming 방지. */
    boolean existsMissionUploadBySlot(UUID roomMemberId, UUID uploadSlotId);

    /** day N 피드: 주어진 슬롯들의 활성·비숨김 영상 전체(전 멤버). */
    List<VideoPost> findFeed(List<UUID> slotIds);
}

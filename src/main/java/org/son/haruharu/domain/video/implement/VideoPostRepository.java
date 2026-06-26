package org.son.haruharu.domain.video.implement;

import java.util.List;
import java.util.UUID;

public interface VideoPostRepository {

    /** 해당 멤버가 주어진 슬롯들에 올린(삭제 안 된) 영상 수. /today의 업로드 진행("3/5") 파생. */
    long countUploaded(UUID roomMemberId, List<UUID> slotIds);
}

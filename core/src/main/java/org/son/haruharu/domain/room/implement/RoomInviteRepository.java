package org.son.haruharu.domain.room.implement;

import java.util.List;
import java.util.Optional;

public interface RoomInviteRepository {

    RoomInvite save(RoomInvite invite);

    Optional<RoomInvite> findByCode(String code);

    /** 방의 코드 이력(최신순). 재발급 시 기존 ACTIVE 코드를 닫는 데, 어드민 조회에는 목록으로 쓴다(#318). */
    List<RoomInvite> findByRoomId(Long roomId);

    /** 입장 처리용 비관적 락 조회(#312) — 정원 경쟁 방지. */
    Optional<RoomInvite> findByCodeForUpdate(String code);
}

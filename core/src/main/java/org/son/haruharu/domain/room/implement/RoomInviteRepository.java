package org.son.haruharu.domain.room.implement;

import java.util.List;
import java.util.Optional;

public interface RoomInviteRepository {

    RoomInvite save(RoomInvite invite);

    Optional<RoomInvite> findByCode(String code);

    /** join의 room-first 잠금 대상을 찾는 스칼라 조회. invite 엔티티를 1차 캐시에 올리지 않는다. */
    Optional<Long> findRoomIdByCode(String code);

    /** 방의 코드 이력(최신순). 재발급 시 기존 ACTIVE 코드를 닫는 데, 어드민 조회에는 목록으로 쓴다(#318). */
    List<RoomInvite> findByRoomId(Long roomId);

    /** room lock 뒤 lifecycle 재검증과 usedCount 갱신을 위한 invite 비관적 락 조회. */
    Optional<RoomInvite> findByCodeForUpdate(String code);
}

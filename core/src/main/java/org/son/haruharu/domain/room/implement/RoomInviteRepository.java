package org.son.haruharu.domain.room.implement;

import java.util.Optional;

public interface RoomInviteRepository {

    RoomInvite save(RoomInvite invite);

    Optional<RoomInvite> findByCode(String code);

    /** 입장 처리용 비관적 락 조회(#312) — 정원 경쟁 방지. */
    Optional<RoomInvite> findByCodeForUpdate(String code);
}

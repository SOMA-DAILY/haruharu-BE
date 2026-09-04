package org.son.haruharu.domain.room.implement;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomInviteReader {

    private final RoomInviteRepository roomInviteRepository;

    public Optional<RoomInvite> findByCode(String code) {
        return roomInviteRepository.findByCode(code);
    }

    public Optional<Long> findRoomIdByCode(String code) {
        return roomInviteRepository.findRoomIdByCode(code);
    }

    /** room lock 뒤 lifecycle 재검증과 usedCount 갱신을 위한 invite 비관적 락 조회. */
    public Optional<RoomInvite> findByCodeForUpdate(String code) {
        return roomInviteRepository.findByCodeForUpdate(code);
    }

    /** 방의 코드 이력(최신순). 재발급·어드민 조회에 쓴다(#318). */
    public List<RoomInvite> findByRoomId(Long roomId) {
        return roomInviteRepository.findByRoomId(roomId);
    }
}

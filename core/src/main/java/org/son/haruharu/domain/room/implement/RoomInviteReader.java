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

    /** 입장 처리 시 정원 경쟁을 막기 위한 비관적 락 조회(#312). */
    public Optional<RoomInvite> findByCodeForUpdate(String code) {
        return roomInviteRepository.findByCodeForUpdate(code);
    }

    /** 방의 코드 이력(최신순). 재발급·어드민 조회에 쓴다(#318). */
    public List<RoomInvite> findByRoomId(Long roomId) {
        return roomInviteRepository.findByRoomId(roomId);
    }
}

package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoomMemberReader {

    private final RoomMemberRepository roomMemberRepository;

    public RoomMember getById(Long id) {
        return roomMemberRepository.findById(id)
                .orElseThrow(RoomMemberNotFoundException::new);
    }

    public List<RoomMember> findByUserId(Long userId) {
        return roomMemberRepository.findByUserId(userId);
    }

    public List<RoomMember> getByRoomId(Long roomId) {
        return roomMemberRepository.findByRoomId(roomId);
    }

    /** 방-유저 멤버십 확인. 참여자가 아니면 예외(거의 모든 방 API의 인가 게이트). */
    public RoomMember getByRoomIdAndUserId(Long roomId, Long userId) {
        return roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(NotRoomMemberException::new);
    }
}

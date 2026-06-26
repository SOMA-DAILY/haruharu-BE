package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoomMemberReader {

    private final RoomMemberRepository roomMemberRepository;

    public List<RoomMember> findByUserId(UUID userId) {
        return roomMemberRepository.findByUserId(userId);
    }

    public List<RoomMember> getByRoomId(UUID roomId) {
        return roomMemberRepository.findByRoomId(roomId);
    }

    /** 방-유저 멤버십 확인. 참여자가 아니면 예외(거의 모든 방 API의 인가 게이트). */
    public RoomMember getByRoomIdAndUserId(UUID roomId, UUID userId) {
        return roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(NotRoomMemberException::new);
    }
}

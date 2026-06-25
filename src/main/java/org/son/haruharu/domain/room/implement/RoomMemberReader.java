package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
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
}

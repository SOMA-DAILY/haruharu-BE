package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoomMemberWriter {

    private final RoomMemberRepository roomMemberRepository;

    public List<RoomMember> saveAll(List<RoomMember> members) {
        return roomMemberRepository.saveAll(members);
    }
}

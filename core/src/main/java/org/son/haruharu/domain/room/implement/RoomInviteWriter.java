package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomInviteWriter {

    private final RoomInviteRepository roomInviteRepository;

    public RoomInvite save(RoomInvite invite) {
        return roomInviteRepository.save(invite);
    }
}

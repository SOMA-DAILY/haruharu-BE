package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;

    public RoomService(RoomReader roomReader, RoomMemberReader roomMemberReader) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
    }

    public RoomResponse getMyRoom(UUID userId) {
        // 유저는 과거 방 이력(재매칭 방지용 보존)을 가질 수 있으므로, 후보 방 id를 모아 종료되지 않은 방을 한 번에 조회한다.
        List<UUID> candidateRoomIds = roomMemberReader.findByUserId(userId).stream()
            .map(RoomMember::roomId)
            .toList();
        Room currentRoom = roomReader.findCurrent(candidateRoomIds)
            .orElseThrow(RoomNotFoundException::new);

        List<RoomMember> members = roomMemberReader.getByRoomId(currentRoom.id());
        return RoomResponse.from(currentRoom, members);
    }
}

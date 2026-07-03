package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final Clock clock;

    public RoomService(RoomReader roomReader, RoomMemberReader roomMemberReader, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.clock = clock;
    }

    public RoomResponse getMyRoom(UUID userId) {
        Room currentRoom = roomReader.findCurrent(candidateRoomIds(userId))
            .orElseThrow(RoomNotFoundException::new);

        List<RoomMember> members = roomMemberReader.getByRoomId(currentRoom.id());
        return RoomResponse.from(currentRoom, members,
                currentRoom.currentDayAt(LocalDateTime.now(clock), UploadSlot.dayStartHour()));
    }

    /** 종료되지 않은(PENDING/ACTIVE) 방 보유 여부. 매칭 큐 참가 차단 등 도메인 간 조율에 쓰인다. */
    public boolean hasCurrentRoom(UUID userId) {
        List<UUID> candidateRoomIds = candidateRoomIds(userId);
        if (candidateRoomIds.isEmpty()) {
            return false;
        }
        return roomReader.findCurrent(candidateRoomIds).isPresent();
    }

    /** 유저는 과거 방 이력(재매칭 방지용 보존)을 가질 수 있으므로, 후보 방 id를 모아 종료되지 않은 방을 한 번에 조회한다. */
    private List<UUID> candidateRoomIds(UUID userId) {
        return roomMemberReader.findByUserId(userId).stream()
            .map(RoomMember::roomId)
            .toList();
    }

    public RoomResponse getRoom(UUID roomId, UUID userId) {
        roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
            .orElseThrow(RoomNotFoundException::new);
        List<RoomMember> members = roomMemberReader.getByRoomId(roomId);
        return RoomResponse.from(room, members,
                room.currentDayAt(LocalDateTime.now(clock), UploadSlot.dayStartHour()));
    }
}

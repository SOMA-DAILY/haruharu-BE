package org.son.haruharu.domain.room.implement;

import java.util.List;
import java.util.UUID;

public interface RoomMemberRepository {

    List<RoomMember> saveAll(List<RoomMember> members);

    List<RoomMember> findByUserId(UUID userId);

    List<RoomMember> findByRoomId(UUID roomId);
}

package org.son.haruharu.domain.room.implement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMemberRepository {

    List<RoomMember> saveAll(List<RoomMember> members);

    RoomMember save(RoomMember member);

    List<RoomMember> findByUserId(Long userId);

    List<RoomMember> findByRoomId(UUID roomId);

    Optional<RoomMember> findByRoomIdAndUserId(UUID roomId, Long userId);

    Optional<RoomMember> findById(UUID id);
}

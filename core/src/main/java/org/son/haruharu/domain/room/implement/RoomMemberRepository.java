package org.son.haruharu.domain.room.implement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository {

    List<RoomMember> saveAll(List<RoomMember> members);

    RoomMember save(RoomMember member);

    List<RoomMember> findByUserId(Long userId);

    List<RoomMember> findByRoomId(Long roomId);

    List<RoomMember> findByRoomIds(Collection<Long> roomIds);

    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    Optional<RoomMember> findById(Long id);
}

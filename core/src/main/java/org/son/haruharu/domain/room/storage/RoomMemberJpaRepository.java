package org.son.haruharu.domain.room.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomMemberJpaRepository extends JpaRepository<RoomMemberEntity, Long> {

    List<RoomMemberEntity> findByUserId(Long userId);

    List<RoomMemberEntity> findByRoomId(Long roomId);

    List<RoomMemberEntity> findByRoomIdIn(Collection<Long> roomIds);

    List<RoomMemberEntity> findByRoomIdOrderByIdAsc(Long roomId);

    Optional<RoomMemberEntity> findByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);
}

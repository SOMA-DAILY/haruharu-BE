package org.son.haruharu.domain.room.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMemberJpaRepository extends JpaRepository<RoomMemberEntity, UUID> {

    List<RoomMemberEntity> findByUserId(Long userId);

    List<RoomMemberEntity> findByRoomId(UUID roomId);

    Optional<RoomMemberEntity> findByRoomIdAndUserId(UUID roomId, Long userId);
}

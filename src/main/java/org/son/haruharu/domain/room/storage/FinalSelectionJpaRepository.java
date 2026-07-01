package org.son.haruharu.domain.room.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FinalSelectionJpaRepository extends JpaRepository<FinalSelectionEntity, UUID> {

    Optional<FinalSelectionEntity> findByRoomMemberId(UUID roomMemberId);

    boolean existsByRoomMemberId(UUID roomMemberId);
}

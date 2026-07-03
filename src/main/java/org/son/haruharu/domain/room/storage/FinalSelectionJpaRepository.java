package org.son.haruharu.domain.room.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinalSelectionJpaRepository extends JpaRepository<FinalSelectionEntity, Long> {

    Optional<FinalSelectionEntity> findByRoomMemberId(Long roomMemberId);

    boolean existsByRoomMemberId(Long roomMemberId);
}

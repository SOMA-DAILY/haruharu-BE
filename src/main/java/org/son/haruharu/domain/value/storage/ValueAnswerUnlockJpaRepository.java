package org.son.haruharu.domain.value.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ValueAnswerUnlockJpaRepository extends JpaRepository<ValueAnswerUnlockEntity, UUID> {

    List<ValueAnswerUnlockEntity> findByRoomMemberId(UUID roomMemberId);

    boolean existsByRoomMemberIdAndUserValueAnswerId(UUID roomMemberId, UUID userValueAnswerId);
}
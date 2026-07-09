package org.son.haruharu.domain.chat.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomEntity, Long> {
    boolean existsByMatchResultId(Long matchResultId);
    Optional<ChatRoomEntity> findByMatchResultId(Long matchResultId);
}

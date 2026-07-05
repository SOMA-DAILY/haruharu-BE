package org.son.haruharu.domain.chat.storage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomEntity, Long> {
    boolean existsByMatchResultId(Long matchResultId);
}

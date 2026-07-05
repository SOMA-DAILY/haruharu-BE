package org.son.haruharu.domain.chat.implement;

import java.util.Optional;

public interface ChatRoomRepository {
    ChatRoom save(ChatRoom chatRoom);
    Optional<ChatRoom> findById(Long id);
    boolean existsByMatchResultId(Long matchResultId);
}

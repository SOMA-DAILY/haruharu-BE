package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

import java.util.Optional;

public interface ChatRoomRepository {
    ChatRoom save(ChatRoom chatRoom);
    Optional<ChatRoom> findById(Long id);
    Optional<Long> lockByIdIncludingDeleted(Long id);
    Optional<Long> lockByIdForShare(Long id);
    boolean existsIncludingDeletedByMatchResultId(Long matchResultId);
    void deleteById(Long id);
    boolean existsByMatchResultId(Long matchResultId);
    Optional<Long> findIdByMatchResultId(Long matchResultId);
    boolean existsByRoomId(Long roomId);
    Optional<ChatRoom> findByRoomId(Long roomId);
    Optional<Long> findIdByRoomId(Long roomId);
    boolean closeGroupByRoomId(Long roomId, LocalDateTime closeAt);
    int closePairsByRoomId(Long roomId, LocalDateTime closeAt);
}

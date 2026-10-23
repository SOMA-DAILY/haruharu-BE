package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

import java.util.List;
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
    /** 아래 room_id 조회 3개는 GROUP 만 본다. 같은 방에 PAIR(#540)도 room_id 를 쓴다. */
    boolean existsByRoomId(Long roomId);
    Optional<ChatRoom> findByRoomId(Long roomId);
    Optional<Long> findIdByRoomId(Long roomId);
    Optional<ChatRoom> findPairByRoomIdAndUsers(Long roomId, Long userA, Long userB);
    /** 원본 방의 아직 닫히지 않은 PAIR(방 종료 처리용, id 순). */
    List<ChatRoom> findOpenPairsByRoomId(Long roomId);
    boolean closeGroupByRoomId(Long roomId, LocalDateTime closeAt);
    int closePairsByRoomId(Long roomId, LocalDateTime closeAt);
}

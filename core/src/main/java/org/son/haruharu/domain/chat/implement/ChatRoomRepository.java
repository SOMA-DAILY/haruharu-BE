package org.son.haruharu.domain.chat.implement;

import java.util.Optional;

public interface ChatRoomRepository {
    ChatRoom save(ChatRoom chatRoom);
    Optional<ChatRoom> findById(Long id);
    boolean existsByMatchResultId(Long matchResultId);
    Optional<Long> findIdByMatchResultId(Long matchResultId);
    boolean existsByRoomId(Long roomId);
    Optional<ChatRoom> findByRoomId(Long roomId);
    Optional<ChatRoom> findByRoomIdForUpdate(Long roomId);

    /** 데모 정리 전용 하드삭제(#266). 전역 soft-delete·UNIQUE(match_result_id) 때문에 재시연 시 재생성이 막히므로 물리 삭제한다. */
    void hardDeleteByMatchResultId(Long matchResultId);
}

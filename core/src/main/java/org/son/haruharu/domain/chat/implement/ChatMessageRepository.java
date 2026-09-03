package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository {
    ChatMessage save(ChatMessage message);
    Optional<ChatMessage> findById(Long id);
    /**
     * 최신순 페이지. cursorId가 null이면 첫 페이지, 아니면 그 id보다 이전(작은) 메시지를 limit개.
     * 삭제된(deletedAt) 메시지는 제외.
     */
    List<ChatMessage> findPage(Long chatRoomId, LocalDateTime joinedAt, Long cursorId, int limit);
    /** 방의 마지막(가장 최신) 메시지. 목록 화면의 미리보기용. */
    Optional<ChatMessage> findLast(Long chatRoomId, LocalDateTime joinedAt);
    /**
     * 안 읽은 메시지 수 = lastReadMessageId보다 큰 id + 내가 보낸 것 제외 + 삭제 제외.
     * 순차 Long id라 커서 비교(id > cursor)로 저렴하게 계산된다.
     */
    long countUnread(Long chatRoomId, Long lastReadMessageId,
                     Long excludeSenderId, LocalDateTime joinedAt);

    long countUnreadByUserId(Long userId);

    /** 데모 정리 전용 하드삭제(#266). 전역 soft-delete라 재시드 시 누적되므로 물리 삭제한다. */
    void hardDeleteByChatRoomId(Long chatRoomId);
}

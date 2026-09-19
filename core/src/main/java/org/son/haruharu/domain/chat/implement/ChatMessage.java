package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

/** 채팅 메시지. createdAt이 전송 시각(BaseEntity). 실제 전송은 #60(STOMP)에서 처리한다. */
public record ChatMessage(
        Long id,
        Long chatRoomId,
        Long senderId,
        String content,
        LocalDateTime createdAt,
        Long videoPostId,
        String clientRequestId
) {
    public ChatMessage(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime createdAt) {
        this(id, chatRoomId, senderId, content, createdAt, null, null);
    }

    public static ChatMessage create(Long chatRoomId, Long senderId, String content) {
        return new ChatMessage(null, chatRoomId, senderId, content, null);
    }
}

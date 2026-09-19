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

    /** 단체 채팅에 저장되는 영상 답장. clientRequestId로 재시도를 멱등 처리한다. */
    public static ChatMessage videoReply(
            Long chatRoomId, Long senderId, String content, Long videoPostId, String clientRequestId) {
        return new ChatMessage(null, chatRoomId, senderId, content, null, videoPostId, clientRequestId);
    }
}

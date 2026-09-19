package org.son.haruharu.domain.chat.business.dto;

import org.son.haruharu.domain.chat.implement.ChatMessage;

import java.time.LocalDateTime;

/** 구독자(/sub)에게 브로드캐스트되는 메시지 payload. */
public record ChatMessageBroadcast(
        Long messageId,
        Long chatRoomId,
        Long senderId,
        String content,
        LocalDateTime sentAt,
            Long videoPostId
) {
    public ChatMessageBroadcast(Long messageId, Long chatRoomId, Long senderId, String content, LocalDateTime sentAt) {
        this(messageId, chatRoomId, senderId, content, sentAt, null);
    }

    public static ChatMessageBroadcast from(ChatMessage message) {
        return new ChatMessageBroadcast(
                message.id(), message.chatRoomId(), message.senderId(), message.content(), message.createdAt(), message.videoPostId());
    }
}

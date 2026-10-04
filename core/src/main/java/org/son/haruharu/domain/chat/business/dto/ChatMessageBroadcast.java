package org.son.haruharu.domain.chat.business.dto;

import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMission;

import java.time.LocalDateTime;

/** 구독자(/sub)에게 브로드캐스트되는 메시지 payload. */
public record ChatMessageBroadcast(
        Long messageId,
        Long chatRoomId,
        Long senderId,
        String content,
        LocalDateTime sentAt,
        String messageType,
        String senderName,
        boolean deleted,
        ChatMission mission
) {
    public ChatMessageBroadcast(Long messageId, Long chatRoomId, Long senderId, String content, LocalDateTime sentAt) {
        this(messageId, chatRoomId, senderId, content, sentAt, "TEXT", null, false, null);
    }

    public static ChatMessageBroadcast from(ChatMessage message) { return from(message, message.visibleMission()); }

    public static ChatMessageBroadcast from(ChatMessage message, ChatMission mission) {
        return new ChatMessageBroadcast(
                message.id(), message.chatRoomId(), message.senderId(), message.visibleContent(), message.createdAt(),
                message.messageType(), message.senderName(), message.adminDeleted(), mission);
    }
}

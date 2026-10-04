package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

/** 채팅 메시지. createdAt이 전송 시각(BaseEntity). 실제 전송은 #60(STOMP)에서 처리한다. */
public record ChatMessage(
        Long id,
        Long chatRoomId,
        Long senderId,
        String content,
        LocalDateTime createdAt,
        ChatMission mission,
        boolean adminDeleted
) {
    public ChatMessage(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime createdAt) {
        this(id, chatRoomId, senderId, content, createdAt, null, false);
    }

    public static ChatMessage create(Long chatRoomId, Long senderId, String content) {
        return new ChatMessage(null, chatRoomId, senderId, content, null);
    }

    public static ChatMessage mission(Long chatRoomId, Long senderId, String content, ChatMission mission) {
        return new ChatMessage(null, chatRoomId, senderId, content, null, mission, false);
    }

    public String visibleContent() { return adminDeleted ? "삭제된 메시지" : content; }
    public String messageType() { return mission == null ? "TEXT" : "ADMIN"; }
    public String senderName() { return mission == null ? null : "하루"; }
    public ChatMission visibleMission() { return adminDeleted ? null : mission; }
}

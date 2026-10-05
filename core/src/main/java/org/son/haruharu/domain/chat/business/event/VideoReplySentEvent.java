package org.son.haruharu.domain.chat.business.event;

public record VideoReplySentEvent(
        Long chatRoomId, Long ownerId, Long senderId, String senderName) {}

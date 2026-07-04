package org.son.haruharu.domain.chat.business.dto;

public record ReadResponse(
        Long chatRoomId,
        long unreadCount
) {}

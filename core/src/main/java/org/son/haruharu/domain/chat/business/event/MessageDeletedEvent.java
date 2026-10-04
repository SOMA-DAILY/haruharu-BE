package org.son.haruharu.domain.chat.business.event;

public record MessageDeletedEvent(Long chatRoomId, Long messageId) {}

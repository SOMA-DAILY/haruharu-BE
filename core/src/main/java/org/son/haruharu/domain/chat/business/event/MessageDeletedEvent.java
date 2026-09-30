package org.son.haruharu.domain.chat.business.event;

/** 운영자가 지운 운영 글(#457). 커밋 후 /sub/chat-rooms/{id}/events 로 MESSAGE_DELETED. */
public record MessageDeletedEvent(Long chatRoomId, Long messageId) {}

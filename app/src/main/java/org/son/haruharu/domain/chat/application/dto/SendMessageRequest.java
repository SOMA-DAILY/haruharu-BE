package org.son.haruharu.domain.chat.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * STOMP 발행(/pub) payload. chatRoomId는 destination 경로에서, senderId는 세션 principal에서 온다.
 * replyToMessageId(#456)는 선택 — 있으면 같은 채팅방 메시지에 대한 답장.
 */
public record SendMessageRequest(
        @NotBlank String content,
        Long replyToMessageId
) {}

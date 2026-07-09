package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReadResponse(
        @Schema(description = "채팅방 id", example = "5") Long chatRoomId,
        @Schema(description = "갱신 후 안 읽은 메시지 수", example = "0") long unreadCount
) {}

package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record GroupChatRoomResponse(
        @Schema(description = "소개팅방에 연결된 GROUP 채팅방 id", example = "88") Long chatRoomId
) {}

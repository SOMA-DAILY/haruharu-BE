package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 내 채팅방 목록. 각 방은 상대 정보 + 마지막 메시지 미리보기 + 안 읽은 개수를 포함한다. */
public record ChatRoomsResponse(
        @Schema(description = "내 채팅방 목록(마지막 메시지 최신순)") List<ChatRoomSummary> chatRooms
) {
    public record ChatRoomSummary(
            @Schema(description = "채팅방 id", example = "5") Long chatRoomId,
            @Schema(description = "상대 최소 정보") PartnerBrief partner,
            @Schema(description = "마지막 메시지 미리보기(없으면 null)", example = "내일 만나요!") String lastMessage,
            @Schema(description = "마지막 메시지 시각(없으면 null)") LocalDateTime lastMessageAt,
            @Schema(description = "안 읽은 메시지 수", example = "2") long unreadCount
    ) {}
}

package org.son.haruharu.domain.chat.business.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 내 채팅방 목록. 각 방은 상대 정보 + 마지막 메시지 미리보기 + 안 읽은 개수를 포함한다. */
public record ChatRoomsResponse(
        List<ChatRoomSummary> chatRooms
) {
    public record ChatRoomSummary(
            Long chatRoomId,
            PartnerBrief partner,
            String lastMessage,
            LocalDateTime lastMessageAt,
            long unreadCount
    ) {}
}

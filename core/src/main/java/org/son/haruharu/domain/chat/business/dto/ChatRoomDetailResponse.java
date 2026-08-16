package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅방 상세. type=DIRECT면 partner(상대 1명)·members=null, type=GROUP면 members(나 제외 참여자 목록)·partner=null.
 */
public record ChatRoomDetailResponse(
        @Schema(description = "채팅방 id", example = "5") Long chatRoomId,
        @Schema(description = "채팅방 유형", example = "GROUP") String type,
        @Schema(description = "DIRECT 상대 최소 정보(GROUP이면 null)", nullable = true) PartnerBrief partner,
        @Schema(description = "GROUP 참여자 목록(나 제외, DIRECT면 null)", nullable = true) List<PartnerBrief> members,
        @Schema(description = "방 생성 시각") LocalDateTime createdAt
) {
    public static ChatRoomDetailResponse direct(Long chatRoomId, PartnerBrief partner, LocalDateTime createdAt) {
        return new ChatRoomDetailResponse(chatRoomId, "DIRECT", partner, null, createdAt);
    }

    public static ChatRoomDetailResponse group(Long chatRoomId, List<PartnerBrief> members, LocalDateTime createdAt) {
        return new ChatRoomDetailResponse(chatRoomId, "GROUP", null, members, createdAt);
    }
}

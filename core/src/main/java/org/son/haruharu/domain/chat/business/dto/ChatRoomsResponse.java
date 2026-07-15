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
            @Schema(description = "상대 최소 정보(상대 탈퇴 시 null)", nullable = true) PartnerBrief partner,
            @Schema(description = "마지막 메시지 미리보기(없거나 상대 탈퇴 시 null)", example = "내일 만나요!",
                    nullable = true) String lastMessage,
            @Schema(description = "마지막 메시지 시각(없거나 상대 탈퇴 시 null)", nullable = true)
            LocalDateTime lastMessageAt,
            @Schema(description = "안 읽은 메시지 수(상대 탈퇴 시 0)", example = "2") long unreadCount,
            @Schema(description = "상대가 탈퇴했는지. true면 partner·lastMessage가 비고 방에 진입할 수 없다",
                    example = "false") boolean partnerWithdrawn
    ) {
        /** 상대가 탈퇴한 방. 대화 기록을 노출하지 않고 탈퇴 사실만 알린다(#255). */
        public static ChatRoomSummary withdrawn(Long chatRoomId) {
            return new ChatRoomSummary(chatRoomId, null, null, null, 0L, true);
        }
    }
}

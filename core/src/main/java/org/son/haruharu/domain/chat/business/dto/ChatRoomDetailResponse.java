package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅방 상세. type=DIRECT·PAIR면 partner(상대 1명)·members=null, type=GROUP면 members(나 제외 참여자 목록)·partner=null.
 * PAIR(#540)는 상대 이름이 방 가명이고, pair_chat 으로 지금 보낼 수 있는지와 다음 열림 시각을 알린다.
 */
public record ChatRoomDetailResponse(
        @Schema(description = "채팅방 id", example = "5") Long chatRoomId,
        @Schema(description = "채팅방 유형", example = "GROUP") String type,
        @Schema(description = "DIRECT·PAIR 상대 최소 정보(GROUP이면 null)", nullable = true) PartnerBrief partner,
        @Schema(description = "GROUP 참여자 목록(나 제외, DIRECT·PAIR면 null)", nullable = true) List<PartnerBrief> members,
        @Schema(description = "방 생성 시각") LocalDateTime createdAt,
        @Schema(description = "원본 소개팅방 id(GROUP·PAIR, DIRECT 면 null)", example = "104", nullable = true) Long roomId,
        @Schema(description = "PAIR 의 대화 시간 상태(PAIR 아니면 null)", nullable = true) PairChatState pairChat
) {
    public record PairChatState(
            @Schema(description = "지금 메시지를 보낼 수 있는지", example = "true") boolean open,
            @Schema(description = "열려 있으면 이번 대화 시간의 끝", nullable = true) LocalDateTime closesAt,
            @Schema(description = "닫혀 있고 다음 대화 시간이 있으면 그 시작", nullable = true) LocalDateTime nextOpensAt
    ) {
        public static final PairChatState CLOSED = new PairChatState(false, null, null);
    }

    public static ChatRoomDetailResponse direct(Long chatRoomId, PartnerBrief partner, LocalDateTime createdAt) {
        return new ChatRoomDetailResponse(chatRoomId, "DIRECT", partner, null, createdAt, null, null);
    }

    public static ChatRoomDetailResponse group(Long chatRoomId, List<PartnerBrief> members, LocalDateTime createdAt,
                                               Long roomId) {
        return new ChatRoomDetailResponse(chatRoomId, "GROUP", null, members, createdAt, roomId, null);
    }

    public static ChatRoomDetailResponse pair(Long chatRoomId, PartnerBrief partner, LocalDateTime createdAt,
                                              Long roomId, PairChatState state) {
        return new ChatRoomDetailResponse(chatRoomId, "PAIR", partner, null, createdAt, roomId, state);
    }
}

package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ChatRoomDetailResponse(
        @Schema(description = "채팅방 id", example = "5") Long chatRoomId,
        @Schema(description = "상대 최소 정보") PartnerBrief partner,
        @Schema(description = "방 생성 시각") LocalDateTime createdAt
) {}

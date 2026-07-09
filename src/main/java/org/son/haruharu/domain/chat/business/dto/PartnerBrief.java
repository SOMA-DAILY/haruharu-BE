package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채팅 상대의 최소 정보(목록·상세 공용). */
public record PartnerBrief(
        @Schema(description = "상대 유저 id", example = "102") Long userId,
        @Schema(description = "상대 닉네임", example = "하늘") String nickname
) {}

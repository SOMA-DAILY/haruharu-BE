package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채팅 상대의 최소 정보(목록·상세 공용). */
public record PartnerBrief(
        @Schema(description = "상대 유저 id", example = "102") Long userId,
        @Schema(description = "상대 닉네임", example = "하늘") String nickname,
        @Schema(description = "상대 대표사진 URL(sorted_order 최소, 없으면 null)",
                example = "https://cdn.haruharu.app/profile/a.jpg", nullable = true) String profileImageUrl
) {}

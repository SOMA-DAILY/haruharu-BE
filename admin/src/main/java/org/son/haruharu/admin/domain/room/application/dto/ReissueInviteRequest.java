package org.son.haruharu.admin.domain.room.application.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 초대코드 재발급 요청(#318). 정원·만료는 생략 가능(생략 시 무제한·무기한). */
public record ReissueInviteRequest(
        @Schema(description = "초대코드 정원(3:3이면 6, null이면 무제한)", example = "6", nullable = true)
        Integer maxUses,
        @Schema(description = "만료 시각(null이면 무기한)", nullable = true)
        LocalDateTime expiresAt
) {
}

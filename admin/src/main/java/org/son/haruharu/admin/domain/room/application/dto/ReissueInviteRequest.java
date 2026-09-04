package org.son.haruharu.admin.domain.room.application.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

/** 초대코드 재발급 요청(#318). maxUses는 관리자가 정하는 실제 방 정원이며 null이면 무제한이다. */
public record ReissueInviteRequest(
        @Schema(description = "관리자가 정하는 실제 방 정원(null이면 무제한)", example = "6", nullable = true)
        @Positive Integer maxUses,
        @Schema(description = "만료 시각(null이면 무기한)", nullable = true)
        LocalDateTime expiresAt
) {
}

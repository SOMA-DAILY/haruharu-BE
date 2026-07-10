package org.son.haruharu.admin.domain.user.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.user.implement.UserStatus;

import java.time.LocalDateTime;

/** 유저 목록 행. 성별·나이·본인인증은 identity 도메인이라 별도 이슈. */
public record AdminUserSummary(
        @Schema(description = "유저 id", example = "901") Long userId,
        @Schema(description = "닉네임(프로필 없으면 null)", example = "여울", nullable = true) String nickname,
        @Schema(description = "지역(프로필 없으면 null)", example = "서울", nullable = true) String region,
        @Schema(description = "상태", example = "ACTIVE") UserStatus status,
        @Schema(description = "가입일") LocalDateTime createdAt
) {
}

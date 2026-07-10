package org.son.haruharu.admin.domain.account.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 어드민 로그인 성공 시 발급되는 토큰 쌍. 클라이언트가 Authorization: Bearer 로 전송한다. */
public record AdminTokenResponse(
        @Schema(description = "어드민 access 토큰(단기)") String accessToken,
        @Schema(description = "어드민 refresh 토큰(장기)") String refreshToken
) {
}

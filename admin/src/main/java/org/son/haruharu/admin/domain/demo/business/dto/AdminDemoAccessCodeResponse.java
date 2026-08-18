package org.son.haruharu.admin.domain.demo.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 현재 데모 접근 코드 상태(#352). configured=false 면 미설정(데모 로그인 닫힘). */
public record AdminDemoAccessCodeResponse(
        @Schema(description = "현재 데모 접근 코드(미설정이면 null)", example = "HARU-DEMO-2026") String code,
        @Schema(description = "코드 설정 여부. false면 데모 로그인이 닫혀 있다", example = "true") boolean configured
) {
    public static AdminDemoAccessCodeResponse configured(String code) {
        return new AdminDemoAccessCodeResponse(code, true);
    }

    public static AdminDemoAccessCodeResponse notConfigured() {
        return new AdminDemoAccessCodeResponse(null, false);
    }
}

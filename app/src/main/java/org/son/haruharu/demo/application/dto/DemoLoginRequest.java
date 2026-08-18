package org.son.haruharu.demo.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 데모 로그인 요청(#352). 운영진이 어드민에서 설정한 접근 코드를 넣어야 데모 세션이 발급된다. */
public record DemoLoginRequest(
        @Schema(description = "데모 접근 코드(어드민 관리)", example = "HARU-DEMO-2026")
        @NotBlank String code
) {
}

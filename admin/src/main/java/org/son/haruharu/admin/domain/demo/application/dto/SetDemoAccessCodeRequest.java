package org.son.haruharu.admin.domain.demo.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 데모 접근 코드 설정/교체 요청(#352). 유출 시 새 값으로 교체하면 옛 코드는 즉시 무효가 된다.
 * 최소 길이를 강제해 브루트포스 가능성을 낮춘다(rate limit 부재 보완) — 짧고 추측 가능한 코드 방지.
 */
public record SetDemoAccessCodeRequest(
        @Schema(description = "새 데모 접근 코드(최소 12자 — 추측 방지)", example = "HARU-DEMO-2026")
        @NotBlank @Size(min = 12, max = 100) String code
) {
}

package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 내 메시지에 반응한 사람(#456). 이름은 GROUP 가명 / DIRECT 실명. */
public record MessageReactorResponse(
        @Schema(description = "표시 이름", example = "선우") String nickname,
        @Schema(description = "반응 종류", example = "LAUGH") String type
) {}

package org.son.haruharu.admin.domain.cohort.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdateApplicationMemoRequest(
        @Schema(description = "운영진 메모(500자 이하). 비우면 지운다", nullable = true, example = "친구와 같은 방 희망")
        @Size(max = 500) String memo
) {
}

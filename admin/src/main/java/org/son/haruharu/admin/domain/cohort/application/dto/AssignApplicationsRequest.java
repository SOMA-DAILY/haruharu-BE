package org.son.haruharu.admin.domain.cohort.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssignApplicationsRequest(
        @Schema(description = "방에 넣을 신청 id(1~50개)", example = "[1, 2, 3]")
        @NotEmpty @Size(max = 50) List<@NotNull Long> applicationIds
) {
}

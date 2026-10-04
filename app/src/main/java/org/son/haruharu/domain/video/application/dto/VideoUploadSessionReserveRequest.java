package org.son.haruharu.domain.video.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VideoUploadSessionReserveRequest(
        @Schema(description = "클라이언트가 만든 업로드 작업 id. 재시도·앱 재실행 때 같은 값을 보내면 같은 세션을 돌려받는다")
        @NotNull UUID jobId,
        @Size(max = 100) String caption
) {}

package org.son.haruharu.domain.chat.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(
        @Schema(description = "읽은 마지막 메시지 id(커서). 이 값 이후 메시지가 안 읽음", example = "1024", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long lastReadMessageId
) {}

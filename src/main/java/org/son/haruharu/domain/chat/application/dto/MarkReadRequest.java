package org.son.haruharu.domain.chat.application.dto;

import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(
        @NotNull Long lastReadMessageId
) {}

package org.son.haruharu.domain.room.application.dto;

import jakarta.validation.constraints.NotNull;

public record FinalSelectionRequest(
        @NotNull Long selectedMemberId
) {}

package org.son.haruharu.domain.room.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FinalSelectionRequest(
        @NotNull UUID selectedMemberId
) {}

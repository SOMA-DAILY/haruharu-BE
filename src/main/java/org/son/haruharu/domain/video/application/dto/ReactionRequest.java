package org.son.haruharu.domain.video.application.dto;

import jakarta.validation.constraints.NotBlank;

public record ReactionRequest(@NotBlank String reactionType) {}

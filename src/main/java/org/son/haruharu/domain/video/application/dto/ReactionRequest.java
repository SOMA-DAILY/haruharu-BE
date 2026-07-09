package org.son.haruharu.domain.video.application.dto;

import jakarta.validation.constraints.NotNull;
import org.son.haruharu.domain.video.implement.ReactionType;

public record ReactionRequest(@NotNull ReactionType reactionType) {}

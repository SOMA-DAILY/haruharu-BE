package org.son.haruharu.domain.chat.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.son.haruharu.domain.video.implement.ReactionType;

/** 메시지 반응 토글 요청(#456). 영상 반응과 같은 6종. */
public record MessageReactionRequest(
        @Schema(description = "반응 종류(LIKE, HEART, LOVE, LAUGH, WOW, FIRE)", example = "LAUGH")
        @NotNull ReactionType type
) {}

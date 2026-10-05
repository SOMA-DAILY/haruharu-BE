package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 반응 토글 결과(#456). 취소면 my_reaction=null. */
public record MessageReactionResponse(
        @Schema(description = "타입별 개수(많은 순)") List<ReactionCountItem> counts,
        @Schema(description = "내 반응(없으면 null)", example = "LAUGH", nullable = true) String myReaction
) {}

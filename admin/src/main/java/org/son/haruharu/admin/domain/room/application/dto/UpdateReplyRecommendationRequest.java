package org.son.haruharu.admin.domain.room.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/** 방 단위 영상 답장 추천 스위치 변경(#560). */
public record UpdateReplyRecommendationRequest(
        @Schema(description = "켜면 true, 끄면 false", example = "true") @NotNull Boolean enabled) {}

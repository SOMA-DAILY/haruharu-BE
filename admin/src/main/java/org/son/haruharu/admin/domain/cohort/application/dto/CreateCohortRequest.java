package org.son.haruharu.admin.domain.cohort.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.son.haruharu.domain.cohort.business.command.CreateCohortCommand;

import java.time.LocalDateTime;

/** 기수 생성(초안). 시각 순서·칸 검증은 도메인이 VALIDATION_ERROR 로 칸별 메시지를 준다. */
public record CreateCohortRequest(
        @Schema(description = "트랙 id", example = "1") @NotNull Long trackId,
        @Schema(description = "트랙 안의 기수 번호(1 이상, 트랙 안에서 유일)", example = "6") Integer sequence,
        @Schema(description = "신청 시작(포함)", example = "2026-10-20T10:00:00") LocalDateTime applyOpensAt,
        @Schema(description = "신청 마감(미포함)", example = "2026-10-26T00:00:00") LocalDateTime applyClosesAt,
        @Schema(description = "방 배정 마감(신청 마감 이후)", example = "2026-10-27T12:00:00") LocalDateTime assignDeadlineAt,
        @Schema(description = "진행 기간 표기(앱 노출)", example = "10월 마지막 주") String periodLabel,
        @Schema(description = "남성 목표 인원(선택)", example = "6", nullable = true) Integer targetMale,
        @Schema(description = "여성 목표 인원(선택)", example = "6", nullable = true) Integer targetFemale
) {
    public CreateCohortCommand toCommand() {
        return new CreateCohortCommand(trackId, sequence, applyOpensAt, applyClosesAt, assignDeadlineAt, periodLabel,
                targetMale, targetFemale);
    }
}

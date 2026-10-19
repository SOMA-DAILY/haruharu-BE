package org.son.haruharu.admin.domain.cohort.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.business.command.UpdateCohortCommand;

import java.time.LocalDateTime;

/** 부분 수정 — 보낸 칸만 바꾼다. 기수 번호는 초안일 때만 바꿀 수 있다. 정산·취소된 기수는 409. */
public record UpdateCohortRequest(
        @Schema(description = "기수 번호(초안만)", example = "6") Integer sequence,
        @Schema(description = "신청 시작(포함)") LocalDateTime applyOpensAt,
        @Schema(description = "신청 마감(미포함)") LocalDateTime applyClosesAt,
        @Schema(description = "방 배정 마감") LocalDateTime assignDeadlineAt,
        @Schema(description = "진행 기간 표기", example = "10월 마지막 주") String periodLabel,
        @Schema(description = "남성 목표 인원", example = "6") Integer targetMale,
        @Schema(description = "여성 목표 인원", example = "6") Integer targetFemale
) {
    public UpdateCohortCommand toCommand() {
        return new UpdateCohortCommand(sequence, applyOpensAt, applyClosesAt, assignDeadlineAt, periodLabel,
                targetMale, targetFemale);
    }
}

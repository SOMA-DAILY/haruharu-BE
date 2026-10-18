package org.son.haruharu.domain.cohort.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;

import java.time.LocalDateTime;

/** 어드민 기수 응답. */
public record CohortResponse(
        @Schema(description = "기수 id", example = "1") Long id,
        @Schema(description = "트랙 id", example = "1") Long trackId,
        @Schema(description = "트랙 이름", example = "서울 대학생") String trackLabel,
        @Schema(description = "트랙 안의 기수 번호", example = "6") int sequence,
        @Schema(description = "표시 이름", example = "서울 대학생 6기") String name,
        @Schema(description = "DRAFT·OPEN·SETTLED·CANCELLED", example = "OPEN") CohortStatus status,
        @Schema(description = "신청 시작(포함)", example = "2026-10-20T10:00:00") LocalDateTime applyOpensAt,
        @Schema(description = "신청 마감(미포함)", example = "2026-10-26T00:00:00") LocalDateTime applyClosesAt,
        @Schema(description = "방 배정 마감", example = "2026-10-27T12:00:00") LocalDateTime assignDeadlineAt,
        @Schema(description = "진행 기간 표기", example = "10월 마지막 주") String periodLabel,
        @Schema(description = "남성 목표 인원(미정이면 null)", example = "6", nullable = true) Integer targetMale,
        @Schema(description = "여성 목표 인원(미정이면 null)", example = "6", nullable = true) Integer targetFemale,
        @Schema(description = "지금 신청을 받는지(OPEN 이고 신청 기간 안)", example = "true") boolean isAccepting,
        @Schema(description = "정산 시각", nullable = true) LocalDateTime settledAt,
        @Schema(description = "만든 운영진", example = "admin:1") String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CohortResponse of(Cohort cohort, RecruitTrack track, LocalDateTime now) {
        return new CohortResponse(cohort.id(), cohort.trackId(), track.label(), cohort.sequence(),
                cohort.displayName(track.label()), cohort.status(), cohort.applyOpensAt(), cohort.applyClosesAt(),
                cohort.assignDeadlineAt(), cohort.periodLabel(), cohort.targetMale(), cohort.targetFemale(),
                cohort.isAcceptingApplications(now), cohort.settledAt(), cohort.createdBy(),
                cohort.createdAt(), cohort.updatedAt());
    }
}

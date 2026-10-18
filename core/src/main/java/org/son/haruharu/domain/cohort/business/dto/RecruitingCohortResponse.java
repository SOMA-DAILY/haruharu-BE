package org.son.haruharu.domain.cohort.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.implement.Cohort;

import java.time.LocalDateTime;

/** 앱에 보이는 열린 기수. */
public record RecruitingCohortResponse(
        @Schema(description = "기수 id(신청 시 사용)", example = "1") Long cohortId,
        @Schema(description = "트랙 안의 기수 번호", example = "6") int sequence,
        @Schema(description = "표시 이름", example = "서울 대학생 6기") String name,
        @Schema(description = "진행 기간 표기", example = "10월 마지막 주") String periodLabel,
        @Schema(description = "신청 시작(포함)", example = "2026-10-20T10:00:00") LocalDateTime applyOpensAt,
        @Schema(description = "신청 마감(미포함)", example = "2026-10-26T00:00:00") LocalDateTime applyClosesAt,
        @Schema(description = "지금 신청할 수 있는지. 시작 전·마감 후면 false", example = "true") boolean isAccepting
) {
    public static RecruitingCohortResponse of(Cohort cohort, String trackLabel, LocalDateTime now) {
        return new RecruitingCohortResponse(cohort.id(), cohort.sequence(), cohort.displayName(trackLabel),
                cohort.periodLabel(), cohort.applyOpensAt(), cohort.applyClosesAt(),
                cohort.isAcceptingApplications(now));
    }
}

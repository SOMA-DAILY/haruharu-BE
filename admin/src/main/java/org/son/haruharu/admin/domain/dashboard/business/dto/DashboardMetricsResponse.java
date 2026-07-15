package org.son.haruharu.admin.domain.dashboard.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** 운영 대시보드 시계열 추이. 기간(range) 내 일자별 가입·매칭 지표. 결제 추이는 1차 범위 제외. */
public record DashboardMetricsResponse(
        @Schema(description = "집계 기간") Range range,
        @Schema(description = "일자별 가입 수(빈 날짜는 0)") List<SignupPoint> signups,
        @Schema(description = "매칭 추이(일자별 + 기간 합계)") Matching matching
) {
    public record Range(
            @Schema(description = "시작일(포함)", example = "2026-07-01") LocalDate from,
            @Schema(description = "종료일(포함)", example = "2026-07-14") LocalDate to
    ) {}

    public record SignupPoint(
            @Schema(description = "일자", example = "2026-07-01") LocalDate date,
            @Schema(description = "해당일 가입 수", example = "57") long count
    ) {}

    public record Matching(
            @Schema(description = "일자별 매칭 지표") List<MatchingPoint> daily,
            @Schema(description = "기간 합계 매칭 지표") MatchingOverall overall
    ) {}

    public record MatchingPoint(
            @Schema(description = "일자(queuedAt 기준)", example = "2026-07-01") LocalDate date,
            @Schema(description = "해당일 매칭 신청 수(전 상태)", example = "40") long applications,
            @Schema(description = "성사(MATCHED) 수", example = "30") long matched,
            @Schema(description = "만료(EXPIRED) 수", example = "10") long expired,
            @Schema(description = "성공률 = matched/(matched+expired). 분모 0이면 null", nullable = true, example = "0.75")
            Double successRate
    ) {}

    public record MatchingOverall(
            @Schema(description = "기간 매칭 신청 수(전 상태)", example = "560") long applications,
            @Schema(description = "기간 성사 수", example = "420") long matched,
            @Schema(description = "기간 만료 수", example = "140") long expired,
            @Schema(description = "기간 성공률. 분모 0이면 null", nullable = true, example = "0.75") Double successRate
    ) {}
}

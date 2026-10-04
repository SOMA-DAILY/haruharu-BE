package org.son.haruharu.admin.domain.dashboard.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** 운영 대시보드 시계열 추이. 기간(range) 내 일자별 가입 지표. 결제 추이는 1차 범위 제외. */
public record DashboardMetricsResponse(
        @Schema(description = "집계 기간") Range range,
        @Schema(description = "일자별 가입 수(빈 날짜는 0)") List<SignupPoint> signups
) {
    public record Range(
            @Schema(description = "시작일(포함)", example = "2026-07-01") LocalDate from,
            @Schema(description = "종료일(포함)", example = "2026-07-14") LocalDate to
    ) {}

    public record SignupPoint(
            @Schema(description = "일자", example = "2026-07-01") LocalDate date,
            @Schema(description = "해당일 가입 수", example = "57") long count
    ) {}
}

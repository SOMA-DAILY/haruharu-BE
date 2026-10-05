package org.son.haruharu.admin.domain.dashboard.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.admin.domain.dashboard.business.AdminDashboardService;
import org.son.haruharu.admin.domain.dashboard.business.dto.DashboardMetricsResponse;
import org.son.haruharu.admin.domain.dashboard.business.dto.DashboardSummaryResponse;
import org.son.haruharu.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admin Dashboard", description = "운영 대시보드 집계")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @Operation(summary = "대시보드 요약 KPI", description = "진행중 방(일차별)·오늘 가입 수.")
    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary() {
        return ApiResponse.ok(adminDashboardService.getSummary());
    }

    @Operation(summary = "대시보드 추이(metrics)",
            description = "기간별 일자 가입 추이. from/to 미지정 시 최근 14일, from>to면 400, 92일 초과 시 clamp.")
    @GetMapping("/metrics")
    public ApiResponse<DashboardMetricsResponse> metrics(
            @Parameter(description = "시작일(포함, 미지정 시 to-13)", example = "2026-07-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일(포함, 미지정 시 오늘)", example = "2026-07-14")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(adminDashboardService.getMetrics(from, to));
    }
}

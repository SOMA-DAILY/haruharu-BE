package org.son.haruharu.admin.domain.report.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.report.business.AdminReportService;
import org.son.haruharu.admin.domain.report.business.dto.AdminReportResponse;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.report.implement.ReportStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Report", description = "유저 신고 처리 — 목록·상세·제재 승인·기각(#553)")
@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    @Operation(summary = "신고 목록", description = "status 필터 + 최신순 페이지. 피신고자 room_ids 로 방 활동 API(userId 필터)를 열어 영상·채팅을 본다.")
    @GetMapping
    public ApiResponse<PageResponse<AdminReportResponse>> reports(
            @Parameter(description = "상태 필터", example = "RECEIVED") @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminReportService.getReports(status, page, size));
    }

    @Operation(summary = "신고 상세", description = "목록 항목 + 같은 피신고자 신고 건수. 없는 id는 404.")
    @GetMapping("/{reportId}")
    public ApiResponse<AdminReportResponse> report(@PathVariable Long reportId) {
        return ApiResponse.ok(adminReportService.getReport(reportId));
    }

    @Operation(summary = "제재 승인", description = "신고 SANCTIONED, 피신고자 BANNED(앱 전 기능 403) + 리프레시 토큰 폐기. 이미 처리된 건은 409.")
    @PostMapping("/{reportId}/approve")
    public ApiResponse<AdminReportResponse> approve(@PathVariable Long reportId, @AuthenticationPrincipal Long adminId) {
        return ApiResponse.ok(adminReportService.approve(reportId, adminId));
    }

    @Operation(summary = "기각", description = "신고 REJECTED. 이미 처리된 건은 409.")
    @PostMapping("/{reportId}/reject")
    public ApiResponse<AdminReportResponse> reject(@PathVariable Long reportId, @AuthenticationPrincipal Long adminId) {
        return ApiResponse.ok(adminReportService.reject(reportId, adminId));
    }
}

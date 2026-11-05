package org.son.haruharu.admin.domain.audit.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.son.haruharu.admin.common.AdminRequestMeta;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.audit.application.dto.CreateAuditReviewRequest;
import org.son.haruharu.admin.domain.audit.application.dto.RegisterExportRequest;
import org.son.haruharu.admin.domain.audit.business.AdminAuditService;
import org.son.haruharu.admin.domain.audit.business.command.SearchAccessLogCommand;
import org.son.haruharu.admin.domain.audit.business.dto.AdminAccessLogResponse;
import org.son.haruharu.admin.domain.audit.business.dto.AdminAuditExportResponse;
import org.son.haruharu.admin.domain.audit.business.dto.AdminAuditReviewResponse;
import org.son.haruharu.admin.domain.audit.business.dto.AdminAuditSummaryResponse;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditAction;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditTargetType;
import org.son.haruharu.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Admin Audit", description = "개인정보 접속기록(#554). 목록·요약·점검은 SUPER, 반출 사유 등록은 SUPER·OPERATOR")
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    public AdminAuditController(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    @Operation(summary = "반출 사유 등록", description = "개인정보를 내려받기 전에 사유를 남긴다. 성공한 뒤에만 다운로드를 진행한다.")
    @PostMapping("/exports")
    public ApiResponse<AdminAuditExportResponse> registerExport(@AuthenticationPrincipal Long adminId,
                                                                @RequestBody @Valid RegisterExportRequest request,
                                                                HttpServletRequest http) {
        return ApiResponse.ok(adminAuditService.registerExport(
                request.toCommand(adminId, AdminRequestMeta.clientIp(http), AdminRequestMeta.userAgent(http))));
    }

    @Operation(summary = "접속기록 목록", description = "최신순. size 최대 100.")
    @GetMapping("/logs")
    public ApiResponse<PageResponse<AdminAccessLogResponse>> logs(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) AdminAuditAction action,
            @RequestParam(required = false) AdminAuditTargetType targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(adminAuditService.getLogs(
                new SearchAccessLogCommand(adminId, action, targetType, targetId, from, to, page, size)));
    }

    @Operation(summary = "접속기록 요약", description = "관리자별 조회·반출 수와 하루 200건 이상 조회한 관리자. 기간 없으면 최근 30일.")
    @GetMapping("/summary")
    public ApiResponse<AdminAuditSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(adminAuditService.getSummary(from, to));
    }

    @Operation(summary = "정기 점검 기록 추가", description = "period 는 YYYY-MM.")
    @PostMapping("/reviews")
    public ApiResponse<AdminAuditReviewResponse> createReview(@AuthenticationPrincipal Long adminId,
                                                              @RequestBody @Valid CreateAuditReviewRequest request) {
        return ApiResponse.ok(adminAuditService.createReview(request.toCommand(adminId)));
    }

    @Operation(summary = "정기 점검 이력", description = "최근 점검부터.")
    @GetMapping("/reviews")
    public ApiResponse<List<AdminAuditReviewResponse>> reviews() {
        return ApiResponse.ok(adminAuditService.getReviews());
    }
}

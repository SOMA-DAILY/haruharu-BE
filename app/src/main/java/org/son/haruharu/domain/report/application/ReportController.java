package org.son.haruharu.domain.report.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.report.application.dto.CreateReportRequest;
import org.son.haruharu.domain.report.business.ReportService;
import org.son.haruharu.domain.report.business.dto.ReportResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "신고", description = "유저 신고 접수 — 미성년자·아동 안전 등 사유 선택(6종) + 상세 설명(선택). 접수 후 운영이 수동 대응한다")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "유저 신고",
            description = "다른 유저를 신고한다. 신고자·대상당 1회만 가능하며 재신고 시 409(REPORT_ALREADY_EXISTS), "
                    + "자기 자신 신고 시 400(CANNOT_REPORT_SELF), 대상이 없으면 404(USER_NOT_FOUND).")
    @PostMapping
    public ApiResponse<ReportResponse> report(Authentication authentication,
                                              @RequestBody @Valid CreateReportRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(reportService.report(userId, request.toCommand()));
    }
}

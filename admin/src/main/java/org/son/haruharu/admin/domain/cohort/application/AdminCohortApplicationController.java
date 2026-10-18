package org.son.haruharu.admin.domain.cohort.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.cohort.application.dto.UpdateApplicationMemoRequest;
import org.son.haruharu.admin.domain.cohort.business.AdminCohortApplicationService;
import org.son.haruharu.admin.domain.cohort.business.dto.AdminCohortApplicationResponse;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Cohort Application", description = "기수 신청자 표(#515). 번호가 들어 있어 조회도 SUPER·OPERATOR")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCohortApplicationController {

    private final AdminCohortApplicationService applicationService;

    public AdminCohortApplicationController(AdminCohortApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Operation(summary = "기수 신청자 목록", description = "상태·성별로 거른다. 우선순위 → 편성 대기 순 → 신청 순. "
            + "없는 기수는 404 COHORT_NOT_FOUND.")
    @GetMapping("/cohorts/{cohortId}/applications")
    public ApiResponse<PageResponse<AdminCohortApplicationResponse>> list(
            @Parameter(description = "기수 id", example = "3") @PathVariable Long cohortId,
            @Parameter(description = "상태", example = "QUEUED") @RequestParam(required = false) CohortApplicationStatus status,
            @Parameter(description = "성별", example = "FEMALE") @RequestParam(required = false) ApplicantGender gender,
            @Parameter(description = "0-based 페이지", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(최대 100)", example = "50") @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(applicationService.getApplications(cohortId, status, gender, page, size));
    }

    @Operation(summary = "운영진 메모 수정", description = "500자 이하. 비우면 지운다. 없는 신청은 404 COHORT_APPLICATION_NOT_FOUND.")
    @PatchMapping("/cohort-applications/{applicationId}/memo")
    public ApiResponse<AdminCohortApplicationResponse> updateMemo(
            @Parameter(description = "신청 id", example = "12") @PathVariable Long applicationId,
            @RequestBody @Valid UpdateApplicationMemoRequest request) {
        return ApiResponse.ok(applicationService.updateMemo(applicationId, request.memo()));
    }

    @Operation(summary = "확인 필요 승인", description = "NEEDS_REVIEW → QUEUED. 성별은 PASS 성별로 맞춘다. "
            + "확인 필요가 아니거나 PASS 성별이 남성·여성이 아니면 409 COHORT_APPLICATION_NOT_APPROVABLE.")
    @PostMapping("/cohort-applications/{applicationId}/approve")
    public ApiResponse<AdminCohortApplicationResponse> approve(
            @Parameter(description = "신청 id", example = "12") @PathVariable Long applicationId) {
        return ApiResponse.ok(applicationService.approve(applicationId));
    }
}

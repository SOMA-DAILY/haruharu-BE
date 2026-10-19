package org.son.haruharu.admin.domain.cohort.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.domain.cohort.application.dto.CreateCohortRequest;
import org.son.haruharu.admin.domain.cohort.application.dto.UpdateCohortRequest;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.cohort.business.CohortService;
import org.son.haruharu.domain.cohort.business.dto.CohortResponse;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Cohort", description = "기수 관리(#510). 쓰기는 SUPER·OPERATOR")
@RestController
@RequestMapping("/api/v1/admin/cohorts")
public class AdminCohortController {

    private final CohortService cohortService;

    public AdminCohortController(CohortService cohortService) {
        this.cohortService = cohortService;
    }

    @Operation(summary = "기수 목록", description = "트랙·상태로 거를 수 있다. 트랙 id 순, 같은 트랙은 최신 기수부터.")
    @GetMapping
    public ApiResponse<List<CohortResponse>> list(
            @Parameter(description = "트랙 id", example = "1") @RequestParam(required = false) Long trackId,
            @Parameter(description = "상태", example = "OPEN") @RequestParam(required = false) CohortStatus status) {
        return ApiResponse.ok(cohortService.getCohorts(trackId, status));
    }

    @Operation(summary = "기수 생성(초안)", description = "같은 트랙에 같은 번호가 있으면 409 COHORT_SEQUENCE_DUPLICATE. "
            + "칸 오류는 400 VALIDATION_ERROR.")
    @PostMapping
    public ApiResponse<CohortResponse> create(@AuthenticationPrincipal Long adminId,
                                              @RequestBody @Valid CreateCohortRequest request) {
        return ApiResponse.ok(cohortService.create(request.toCommand(), "admin:" + adminId));
    }

    @Operation(summary = "기수 수정", description = "보낸 칸만 바꾼다. 번호는 초안만. 정산·취소된 기수는 409 COHORT_NOT_EDITABLE.")
    @PatchMapping("/{cohortId}")
    public ApiResponse<CohortResponse> update(
            @Parameter(description = "기수 id", example = "1") @PathVariable Long cohortId,
            @RequestBody @Valid UpdateCohortRequest request) {
        return ApiResponse.ok(cohortService.update(cohortId, request.toCommand()));
    }

    @Operation(summary = "기수 공개", description = "DRAFT → OPEN. 트랙에 이미 OPEN 기수가 있으면 409 COHORT_OPEN_ALREADY_EXISTS, "
            + "비활성 트랙이면 409 RECRUIT_TRACK_INACTIVE, 신청 마감이 지났으면 400.")
    @PostMapping("/{cohortId}/publish")
    public ApiResponse<CohortResponse> publish(
            @Parameter(description = "기수 id", example = "1") @PathVariable Long cohortId) {
        return ApiResponse.ok(cohortService.publish(cohortId));
    }

    @Operation(summary = "기수 취소", description = "DRAFT·OPEN → CANCELLED. 그 외 상태는 409 COHORT_NOT_CANCELLABLE.")
    @PostMapping("/{cohortId}/cancel")
    public ApiResponse<CohortResponse> cancel(
            @Parameter(description = "기수 id", example = "1") @PathVariable Long cohortId) {
        return ApiResponse.ok(cohortService.cancel(cohortId));
    }
}

package org.son.haruharu.admin.domain.mission.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.domain.mission.application.dto.CreateMissionRequest;
import org.son.haruharu.admin.domain.mission.application.dto.ChangeMissionStatusRequest;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.mission.business.AdminMissionService;
import org.son.haruharu.admin.domain.mission.business.dto.AdminMissionResponse;
import org.son.haruharu.admin.domain.mission.business.dto.AdminMissionRevisionResponse;
import org.son.haruharu.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Mission", description = "미션 문구 카탈로그 관리(SUPER 전용). 활성 미션이 방 배정 후보 풀이 된다.")
@RestController
@RequestMapping("/api/v1/admin/missions")
public class AdminMissionController {

    private final AdminMissionService adminMissionService;

    public AdminMissionController(AdminMissionService adminMissionService) {
        this.adminMissionService = adminMissionService;
    }

    @Operation(summary = "미션 생성", description = "논리 미션과 revision 1, eligibility를 원자적으로 생성합니다. SUPER 전용.")
    @PostMapping
    public ApiResponse<AdminMissionResponse> create(@RequestBody @Valid CreateMissionRequest request) {
        return ApiResponse.ok(adminMissionService.create(request.toCommand()));
    }

    @Operation(summary = "미션 목록", description = "활성/비활성 전체. SUPER 전용.")
    @GetMapping
    public ApiResponse<PageResponse<AdminMissionResponse>> list(
            @Parameter(description = "0-based 페이지", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(최대 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminMissionService.getMissions(page, size));
    }

    @Operation(summary = "미션 revision 이력", description = "최신 revision부터 반환합니다. SUPER 전용.")
    @GetMapping("/{id}/revisions")
    public ApiResponse<PageResponse<AdminMissionRevisionResponse>> revisions(
            @Parameter(description = "미션 id", example = "1") @PathVariable Long id,
            @Parameter(description = "0-based 페이지", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(최대 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminMissionService.getRevisions(id, page, size));
    }

    @Operation(summary = "새 미션 revision 발행",
            description = "기존 revision은 유지하고 새 revision과 eligibility를 발행한 뒤 현재 포인터를 교체합니다. SUPER 전용.")
    @PostMapping("/{id}/revisions")
    public ApiResponse<AdminMissionResponse> publishRevision(
            @Parameter(description = "미션 id", example = "1") @PathVariable Long id,
            @RequestBody @Valid CreateMissionRequest request) {
        return ApiResponse.ok(adminMissionService.publishRevision(id, request.toCommand()));
    }

    @Operation(summary = "미션 활성 상태 변경",
            description = "비활성화하면 신규 배정 후보에서만 제외되고 과거 배정 카드는 유지됩니다. SUPER 전용.")
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminMissionResponse> changeStatus(
            @Parameter(description = "미션 id", example = "1") @PathVariable Long id,
            @RequestBody @Valid ChangeMissionStatusRequest request) {
        return ApiResponse.ok(adminMissionService.changeStatus(id, request.active()));
    }
}

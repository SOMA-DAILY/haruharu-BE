package org.son.haruharu.admin.domain.mission.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.domain.mission.application.dto.CreateMissionRequest;
import org.son.haruharu.admin.domain.mission.application.dto.UpdateMissionRequest;
import org.son.haruharu.admin.domain.mission.business.AdminMissionService;
import org.son.haruharu.admin.domain.mission.business.dto.AdminMissionResponse;
import org.son.haruharu.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Mission", description = "미션 문구 카탈로그 관리(SUPER 전용). 활성 미션이 방 배정 후보 풀이 된다.")
@RestController
@RequestMapping("/api/v1/admin/missions")
public class AdminMissionController {

    private final AdminMissionService adminMissionService;

    public AdminMissionController(AdminMissionService adminMissionService) {
        this.adminMissionService = adminMissionService;
    }

    @Operation(summary = "미션 생성", description = "문구 중복 시 409. 생성 시 활성(active=true) 상태. SUPER 전용.")
    @PostMapping
    public ApiResponse<AdminMissionResponse> create(@RequestBody @Valid CreateMissionRequest request) {
        return ApiResponse.ok(adminMissionService.create(request.toCommand()));
    }

    @Operation(summary = "미션 목록", description = "활성/비활성 전체. SUPER 전용.")
    @GetMapping
    public ApiResponse<List<AdminMissionResponse>> list() {
        return ApiResponse.ok(adminMissionService.getMissions());
    }

    @Operation(summary = "미션 수정",
            description = "보낸 필드만 부분 갱신(문구·포인트·활성). active=false로 소프트 삭제(배정 후보 제외). SUPER 전용.")
    @PatchMapping("/{id}")
    public ApiResponse<AdminMissionResponse> update(
            @Parameter(description = "미션 id", example = "1") @PathVariable Long id,
            @RequestBody @Valid UpdateMissionRequest request) {
        return ApiResponse.ok(adminMissionService.update(id, request.toCommand()));
    }
}

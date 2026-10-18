package org.son.haruharu.admin.domain.cohort.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.domain.cohort.application.dto.CreateRecruitTrackRequest;
import org.son.haruharu.admin.domain.cohort.application.dto.UpdateRecruitTrackRequest;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.cohort.business.RecruitTrackService;
import org.son.haruharu.domain.cohort.business.dto.RecruitTrackResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Recruit Track", description = "모집 트랙 관리(#510). 쓰기는 SUPER·OPERATOR")
@RestController
@RequestMapping("/api/v1/admin/recruit-tracks")
public class AdminRecruitTrackController {

    private final RecruitTrackService recruitTrackService;

    public AdminRecruitTrackController(RecruitTrackService recruitTrackService) {
        this.recruitTrackService = recruitTrackService;
    }

    @Operation(summary = "트랙 목록", description = "활성·비활성 전체를 표시 순서대로.")
    @GetMapping
    public ApiResponse<List<RecruitTrackResponse>> list() {
        return ApiResponse.ok(recruitTrackService.getTracks());
    }

    @Operation(summary = "트랙 생성", description = "키 중복 시 409 RECRUIT_TRACK_KEY_DUPLICATE. 칸 오류는 400 VALIDATION_ERROR.")
    @PostMapping
    public ApiResponse<RecruitTrackResponse> create(@RequestBody @Valid CreateRecruitTrackRequest request) {
        return ApiResponse.ok(recruitTrackService.create(request.toCommand()));
    }

    @Operation(summary = "트랙 수정", description = "보낸 칸만 바꾼다(key 제외). 공개 중인 기수가 있는데 비활성화하면 409.")
    @PatchMapping("/{trackId}")
    public ApiResponse<RecruitTrackResponse> update(
            @Parameter(description = "트랙 id", example = "1") @PathVariable Long trackId,
            @RequestBody @Valid UpdateRecruitTrackRequest request) {
        return ApiResponse.ok(recruitTrackService.update(trackId, request.toCommand()));
    }
}

package org.son.haruharu.admin.domain.mission.common.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.admin.domain.mission.common.application.dto.RoomMissionRequest;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.mission.common.business.RoomMissionService;
import org.son.haruharu.domain.mission.common.business.dto.MissionPageResponse;
import org.son.haruharu.domain.mission.common.business.dto.RoomMissionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Room Mission", description = "미션 정의·일정·상태 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/rooms/{roomId}/missions")
public class AdminRoomMissionController {
    private final RoomMissionService missions;

    @GetMapping
    public ApiResponse<MissionPageResponse> list(@PathVariable Long roomId,
            @RequestParam(required = false) Long after, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(missions.list(roomId, after, size));
    }
    @GetMapping("/{missionId}")
    public ApiResponse<RoomMissionResponse> get(@PathVariable Long roomId, @PathVariable Long missionId) {
        return ApiResponse.ok(missions.get(roomId, missionId));
    }
    @Operation(summary = "미션 개설", description = "announcement 생략 시 채팅과 무관한 미션만 생성. opens_at 필수.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoomMissionResponse> create(@PathVariable Long roomId, @jakarta.validation.Valid @RequestBody RoomMissionRequest request,
                                                   Authentication auth) {
        return ApiResponse.ok(missions.create(roomId, request.toCommand(), actor(auth)));
    }
    @Operation(summary = "시작 전 미션 정의 교체", description = "전체 교체. closes_at null은 방 종료. 안내는 별도 API 사용.")
    @PutMapping("/{missionId}")
    public ApiResponse<RoomMissionResponse> update(@PathVariable Long roomId, @PathVariable Long missionId,
                                                   @jakarta.validation.Valid @RequestBody RoomMissionRequest request, Authentication auth) {
        return ApiResponse.ok(missions.update(roomId, missionId, request.toCommand(), actor(auth)));
    }
    @PostMapping("/{missionId}/cancel")
    public ApiResponse<RoomMissionResponse> cancel(@PathVariable Long roomId, @PathVariable Long missionId,
                                                   Authentication auth) {
        return ApiResponse.ok(missions.cancel(roomId, missionId, actor(auth)));
    }
    private String actor(Authentication auth) { return "admin:" + (Long) auth.getPrincipal(); }
}

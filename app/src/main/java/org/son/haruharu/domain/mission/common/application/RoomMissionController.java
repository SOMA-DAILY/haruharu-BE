package org.son.haruharu.domain.mission.common.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.mission.common.business.RoomMissionService;
import org.son.haruharu.domain.mission.common.business.dto.MissionPageResponse;
import org.son.haruharu.domain.mission.common.business.dto.RoomMissionResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "방 미션", description = "참가자에게 시작된 미션만 공개. 조회는 메시지나 응답을 생성하지 않는다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rooms/{roomId}/mission-instances")
public class RoomMissionController {

    private final RoomMissionService missions;

    @GetMapping
    public ApiResponse<MissionPageResponse> list(@PathVariable Long roomId, Authentication auth,
            @RequestParam(required = false) Long after, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(missions.listForParticipant(roomId, (Long) auth.getPrincipal(), after, size));
    }

    @Operation(summary = "채팅에 표시된 미션 일괄 조회", description = "ids 최대 100개. 다른 방·시작 전·없는 ID는 결과에서 제외.")
    @GetMapping("/lookup")
    public ApiResponse<List<RoomMissionResponse>> lookup(@PathVariable Long roomId,
            @RequestParam List<Long> ids, Authentication auth) {
        return ApiResponse.ok(missions.findForParticipant(roomId, (Long) auth.getPrincipal(), ids));
    }

    @GetMapping("/{missionId}")
    public ApiResponse<RoomMissionResponse> get(@PathVariable Long roomId, @PathVariable Long missionId, Authentication auth) {
        return ApiResponse.ok(missions.getForParticipant(roomId, missionId, (Long) auth.getPrincipal()));
    }

}

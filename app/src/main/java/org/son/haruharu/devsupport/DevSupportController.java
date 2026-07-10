package org.son.haruharu.devsupport;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.devsupport.dto.DevClockResponse;
import org.son.haruharu.devsupport.dto.DevMatchingRunResponse;
import org.son.haruharu.devsupport.dto.DevRoomCreateRequest;
import org.son.haruharu.devsupport.dto.DevRoomResponse;
import org.son.haruharu.devsupport.dto.DevRoomTimeRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * [dev·local 전용, #180] 테스트 방 즉시 생성 + 가상 시계 API.
 * Swagger 비노출(@Hidden), JWT 인증 필요(SecurityConfig permitAll 추가 금지 — #158 방침).
 * 운영 프로필에서는 빈 자체가 로드되지 않는다.
 */
@Hidden
@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/v1/dev")
public class DevSupportController {

    private final DevRoomService devRoomService;

    public DevSupportController(DevRoomService devRoomService) {
        this.devRoomService = devRoomService;
    }

    @PostMapping("/rooms")
    public ApiResponse<DevRoomResponse> createRoom(@Valid @RequestBody DevRoomCreateRequest request) {
        return ApiResponse.ok(devRoomService.createRoom(request.userIds(), request.day(), request.time()));
    }

    @DeleteMapping("/rooms/{roomId}")
    public ApiResponse<Void> deleteRoom(@PathVariable Long roomId) {
        devRoomService.deleteRoom(roomId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/rooms/{roomId}/time")
    public ApiResponse<DevClockResponse> setRoomTime(@PathVariable Long roomId,
                                                     @Valid @RequestBody DevRoomTimeRequest request) {
        return ApiResponse.ok(devRoomService.setRoomTime(roomId, request.day(), request.time()));
    }

    @PostMapping("/rooms/{roomId}/terminate")
    public ApiResponse<Void> terminateRoom(@PathVariable Long roomId) {
        devRoomService.terminateRoom(roomId);
        return ApiResponse.ok(null);
    }

    /** 발표 시각이 지난 모든 ACTIVE 방의 미매칭 멤버에게 위로 구름을 지급한다(가상 시각 기준). */
    @PostMapping("/consolation")
    public ApiResponse<Map<String, Integer>> grantConsolation() {
        return ApiResponse.ok(Map.of("granted", devRoomService.grantConsolation()));
    }

    @PostMapping("/matching/run")
    public ApiResponse<DevMatchingRunResponse> runMatching(
            @RequestParam(required = false) LocalDate date) {
        return ApiResponse.ok(devRoomService.runMatching(date));
    }

    @GetMapping("/clock")
    public ApiResponse<DevClockResponse> clockStatus() {
        return ApiResponse.ok(devRoomService.clockStatus());
    }

    @DeleteMapping("/clock")
    public ApiResponse<DevClockResponse> resetClock() {
        return ApiResponse.ok(devRoomService.resetClock());
    }
}

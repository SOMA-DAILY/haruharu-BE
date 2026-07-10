package org.son.haruharu.devsupport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * JWT 인증 필요(SecurityConfig permitAll 추가 금지 — #158 방침).
 * 운영 프로필에서는 빈 자체가 로드되지 않아 운영 스웨거에도 노출되지 않는다(#184).
 */
@Tag(name = "Dev 테스트 지원", description = "dev·local 전용. 가상 시계는 서버 전역이라 사용 후 리셋 필수(미리셋 시 4시간 후 자동 해제).")
@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/v1/dev")
public class DevSupportController {

    private final DevRoomService devRoomService;

    public DevSupportController(DevRoomService devRoomService) {
        this.devRoomService = devRoomService;
    }

    @Operation(summary = "테스트 방 즉시 생성",
            description = "지정 계정(2~6명, 본인인증 완료·진행 중 방 없음)으로 방을 즉시 ACTIVE 생성. "
                    + "day(1~3, 기본 1)는 started_at을 역산해 방이 몇 일차인지 결정, time(HH:mm)을 주면 가상 시계도 그 시각으로 고정.")
    @PostMapping("/rooms")
    public ApiResponse<DevRoomResponse> createRoom(@Valid @RequestBody DevRoomCreateRequest request) {
        return ApiResponse.ok(devRoomService.createRoom(request.userIds(), request.day(), request.time()));
    }

    @Operation(summary = "테스트 방 삭제", description = "방 + 파생 데이터(영상·선택·매치·채팅) 하드 삭제. 반복 테스트용.")
    @DeleteMapping("/rooms/{roomId}")
    public ApiResponse<Void> deleteRoom(@PathVariable Long roomId) {
        devRoomService.deleteRoom(roomId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "방 시간대 지정(가상 시계 고정)",
            description = "가상 시계를 해당 방의 Day/시각 절대 시각으로 고정. started_at은 변경하지 않는다. "
                    + "하루 경계 09:00 — 새벽(00~09시)은 해당 Day의 익일 날짜로 계산. 최종선택 창은 Day3 18~22시.")
    @PutMapping("/rooms/{roomId}/time")
    public ApiResponse<DevClockResponse> setRoomTime(@PathVariable Long roomId,
                                                     @Valid @RequestBody DevRoomTimeRequest request) {
        return ApiResponse.ok(devRoomService.setRoomTime(roomId, request.day(), request.time()));
    }

    @Operation(summary = "방 수동 종료", description = "ACTIVE 방을 즉시 ENDED로 전환(09:00 종료 배치 대체).")
    @PostMapping("/rooms/{roomId}/terminate")
    public ApiResponse<Void> terminateRoom(@PathVariable Long roomId) {
        devRoomService.terminateRoom(roomId);
        return ApiResponse.ok(null);
    }

    /** 발표 시각이 지난 모든 ACTIVE 방의 미매칭 멤버에게 위로 구름을 지급한다(가상 시각 기준). */
    @Operation(summary = "위로 구름 수동 지급", description = "발표 시각(Day3 22:00) 지난 모든 ACTIVE 방의 미매칭 멤버에게 지급(22:00 배치 대체, 멱등).")
    @PostMapping("/consolation")
    public ApiResponse<Map<String, Integer>> grantConsolation() {
        return ApiResponse.ok(Map.of("granted", devRoomService.grantConsolation()));
    }

    @Operation(summary = "매칭 배치 수동 실행", description = "큐의 남3·여3 확정 + 잔여 신청자 EXPIRED·환불 후 해당 날짜 방 즉시 활성화(22:00 배치 대체).")
    @PostMapping("/matching/run")
    public ApiResponse<DevMatchingRunResponse> runMatching(
            @RequestParam(required = false) LocalDate date) {
        return ApiResponse.ok(devRoomService.runMatching(date));
    }

    @Operation(summary = "가상 시계 상태 조회", description = "fixed=true면 고정 중. 테스트 시작 전 다른 사람이 쓰는 중인지 확인 용도.")
    @GetMapping("/clock")
    public ApiResponse<DevClockResponse> clockStatus() {
        return ApiResponse.ok(devRoomService.clockStatus());
    }

    @Operation(summary = "가상 시계 해제(실시간 복귀)", description = "테스트 종료 시 필수 호출.")
    @DeleteMapping("/clock")
    public ApiResponse<DevClockResponse> resetClock() {
        return ApiResponse.ok(devRoomService.resetClock());
    }
}

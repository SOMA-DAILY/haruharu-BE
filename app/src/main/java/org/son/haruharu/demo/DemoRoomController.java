package org.son.haruharu.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.room.business.DemoRoomService;
import org.son.haruharu.domain.room.business.dto.DemoRoomResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모 방 조작(#265). 데모 계정 인가는 DemoModeInterceptor가 /api/v1/demo/** 경로에 강제하므로
 * 컨트롤러는 별도 게이트 호출 없이 인증 principal(userId)만 사용한다.
 */
@Tag(name = "Demo Room", description = "앱스토어 심사용 데모 방(데모 계정 전용)")
@RestController
@RequestMapping("/api/v1/demo/room")
public class DemoRoomController {

    private final DemoRoomService demoRoomService;

    public DemoRoomController(DemoRoomService demoRoomService) {
        this.demoRoomService = demoRoomService;
    }

    @Operation(summary = "데모 방 생성", description = "Day1 데모 방을 생성하고 호출자를 멤버로 추가. 이미 있으면 그대로 반환.")
    @PostMapping
    public ApiResponse<DemoRoomResponse> createRoom(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(demoRoomService.createOrGet(userId));
    }

    @Operation(summary = "데모 방 Day 진행", description = "demo_day를 1→2→3으로 진행(totalDays로 캡). Day3에서 최종선택이 열린다.")
    @PostMapping("/advance-day")
    public ApiResponse<DemoRoomResponse> advanceDay(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(demoRoomService.advanceDay(userId));
    }
}

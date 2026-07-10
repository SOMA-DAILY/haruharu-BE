package org.son.haruharu.admin.domain.room.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.room.business.AdminRoomService;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomDetailResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomSummary;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admin Session", description = "운영 세션(방) 조회")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminRoomController {

    private final AdminRoomService adminRoomService;

    public AdminRoomController(AdminRoomService adminRoomService) {
        this.adminRoomService = adminRoomService;
    }

    @Operation(summary = "방 목록", description = "status·예정일 필터 + 페이지네이션. 각 방에 성비·일차·참여자 수 포함.")
    @GetMapping("/rooms")
    public ApiResponse<PageResponse<AdminRoomSummary>> rooms(
            @Parameter(description = "방 상태 필터", example = "ACTIVE") @RequestParam(required = false) RoomStatus status,
            @Parameter(description = "예정일 필터", example = "2026-07-09")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledDate,
            @Parameter(description = "0-based 페이지", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(최대 100)", example = "20") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminRoomService.getRooms(status, scheduledDate, page, size));
    }

    @Operation(summary = "방 상세", description = "방 정보 + 참여자 목록. 없는 id는 404.")
    @GetMapping("/rooms/{roomId}")
    public ApiResponse<AdminRoomDetailResponse> room(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId) {
        return ApiResponse.ok(adminRoomService.getRoom(roomId));
    }
}

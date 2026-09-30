package org.son.haruharu.admin.domain.room.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.admin.domain.room.application.dto.ScheduleItemRequest;
import org.son.haruharu.admin.domain.room.business.AdminRoomScheduleService;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.roomschedule.business.dto.RoomScheduleItemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Room Schedule", description = "방 편성(운영 글 예약, 프로필 항목 공개 시각)")
@RestController
@RequestMapping("/api/v1/admin/rooms/{roomId}/schedule")
public class AdminRoomScheduleController {

    private final AdminRoomScheduleService adminRoomScheduleService;

    public AdminRoomScheduleController(AdminRoomScheduleService adminRoomScheduleService) {
        this.adminRoomScheduleService = adminRoomScheduleService;
    }

    @Operation(summary = "방 편성 목록", description = "run_at 순. day 는 달력 날짜 기준(방 시작 전이면 0 이하).")
    @GetMapping
    public ApiResponse<List<RoomScheduleItemResponse>> list(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId) {
        return ApiResponse.ok(adminRoomScheduleService.list(roomId));
    }

    @Operation(summary = "편성 행 추가",
            description = "run_at 이 null 이면 지금(1분 안 발송). 운영 글이나 공개 항목 중 하나는 필수, 글자 수(500/15/30) 외 검증 없음. "
                    + "400 은 VALIDATION_ERROR + error.fields. SUPER·OPERATOR 전용.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoomScheduleItemResponse> add(@PathVariable Long roomId, @RequestBody ScheduleItemRequest request,
                                                     Authentication authentication) {
        return ApiResponse.ok(adminRoomScheduleService.add(roomId, request.toCommand(), adminId(authentication)));
    }

    @Operation(summary = "편성 행 수정",
            description = "SCHEDULED 행만(아니면 409 SCHEDULE_ITEM_NOT_EDITABLE). run_at·reveal_fields·push 가 null 이면 그대로.")
    @PatchMapping("/{itemId}")
    public ApiResponse<RoomScheduleItemResponse> update(@PathVariable Long roomId, @PathVariable Long itemId,
                                                        @RequestBody ScheduleItemRequest request,
                                                        Authentication authentication) {
        return ApiResponse.ok(adminRoomScheduleService.update(roomId, itemId, request.toCommand(), adminId(authentication)));
    }

    @Operation(summary = "지금 보내기", description = "run_at 을 지금으로 바꾼다(1분 잡이 발송). SCHEDULED 행만.")
    @PostMapping("/{itemId}/send-now")
    public ApiResponse<RoomScheduleItemResponse> sendNow(@PathVariable Long roomId, @PathVariable Long itemId,
                                                         Authentication authentication) {
        return ApiResponse.ok(adminRoomScheduleService.sendNow(roomId, itemId, adminId(authentication)));
    }

    @Operation(summary = "예약 취소", description = "SCHEDULED → CANCELED. 취소한 공개 항목은 공개되지 않는다.")
    @PostMapping("/{itemId}/cancel")
    public ApiResponse<RoomScheduleItemResponse> cancel(@PathVariable Long roomId, @PathVariable Long itemId,
                                                        Authentication authentication) {
        return ApiResponse.ok(adminRoomScheduleService.cancel(roomId, itemId, adminId(authentication)));
    }

    @Operation(summary = "보낸 운영 글 삭제",
            description = "SENT 이고 보낸 메시지가 있을 때만. 앱에는 '삭제된 메시지' 로 보인다. 두 번째 호출도 200(멱등).")
    @DeleteMapping("/{itemId}/message")
    public ApiResponse<RoomScheduleItemResponse> deleteMessage(@PathVariable Long roomId, @PathVariable Long itemId) {
        return ApiResponse.ok(adminRoomScheduleService.deleteSentMessage(roomId, itemId));
    }

    private Long adminId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}

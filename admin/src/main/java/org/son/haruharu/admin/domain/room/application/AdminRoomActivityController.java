package org.son.haruharu.admin.domain.room.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.room.business.AdminRoomActivityService;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomActivityResponse;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.common.ApiErrorDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/rooms/{roomId}")
@Tag(name = "Admin Room Activity", description = "방 영상·그룹채팅·반응·익명질문 조회")
public class AdminRoomActivityController {
    private final AdminRoomActivityService service;
    public AdminRoomActivityController(AdminRoomActivityService service) { this.service = service; }

    @GetMapping("/activities")
    @Operation(summary = "방 활동 내역", description = "type: VIDEO/CHAT/REACTION/QUESTION. 질문 한 건에 현재 답변을 포함한다. day는 질문 생성 시각의 방 시작 기준 일차. userId는 발신·수신 참여자. 관리자에게 방 가명과 ID를 표시한다. order: asc/desc.")
    public ApiResponse<PageResponse<AdminRoomActivityResponse>> getActivities(@PathVariable Long roomId,
            @RequestParam(required = false) Integer day, @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size, @RequestParam(defaultValue = "desc") String order) {
        return ApiResponse.ok(service.getActivities(roomId, day, userId, type, page, size, order));
    }

    @GetMapping("/activity/summary")
    @Operation(summary = "방 활동 요약", description = "일차·참여자 필터에 해당하는 전체 활동 집계. 현재 페이지와 무관.")
    public ApiResponse<AdminRoomActivityResponse.Summary> getSummary(@PathVariable Long roomId,
            @RequestParam(required = false) Integer day, @RequestParam(required = false) Long userId) {
        return ApiResponse.ok(service.getSummary(roomId, day, userId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter() {
        return ResponseEntity.badRequest().body(ApiResponse.error(new ApiErrorDetail(
                "ADMIN_ROOM_ACTIVITY_INVALID_FILTER", "일차, 참여자 또는 페이지 조건을 확인해 주세요.")));
    }
}

package org.son.haruharu.admin.domain.room.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.room.application.dto.AssignGenderRequest;
import org.son.haruharu.admin.domain.room.application.dto.CreateAdminRoomRequest;
import org.son.haruharu.admin.domain.room.application.dto.ReissueInviteRequest;
import org.son.haruharu.admin.domain.room.business.AdminRoomCommandService;
import org.son.haruharu.admin.domain.room.business.AdminRoomService;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomCreatedResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomDetailResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomInviteResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomMemberResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomSummary;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Admin Session", description = "운영 세션(방) 조회")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminRoomController {

    private final AdminRoomService adminRoomService;
    private final AdminRoomCommandService adminRoomCommandService;

    public AdminRoomController(AdminRoomService adminRoomService,
                               AdminRoomCommandService adminRoomCommandService) {
        this.adminRoomService = adminRoomService;
        this.adminRoomCommandService = adminRoomCommandService;
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

    @Operation(summary = "방 생성 + 초대코드 발급",
            description = "방 스케줄과 Day별 오전·오후 미션 슬롯을 직접 입력해 PENDING 방을 만들고 "
                    + "초대코드를 함께 발급한다. 방·슬롯·코드는 한 트랜잭션으로 저장된다. SUPER·OPERATOR 전용.")
    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminRoomCreatedResponse> createRoom(
            @RequestBody @Valid CreateAdminRoomRequest request, Authentication authentication) {
        Long adminId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(adminRoomCommandService.createRoomWithInvite(request.toCommand(), adminId));
    }

    @Operation(summary = "초대코드 재발급",
            description = "기존 ACTIVE 코드를 EXPIRED로 닫고 새 코드를 발급한다(방당 ACTIVE 1개). SUPER·OPERATOR 전용.")
    @PostMapping("/rooms/{roomId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminRoomInviteResponse> reissueInvite(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId,
            @RequestBody(required = false) @Valid ReissueInviteRequest request, Authentication authentication) {
        Long adminId = (Long) authentication.getPrincipal();
        Integer maxUses = request == null ? null : request.maxUses();
        LocalDateTime expiresAt = request == null ? null : request.expiresAt();
        return ApiResponse.ok(adminRoomCommandService.reissueInvite(roomId, maxUses, expiresAt, adminId));
    }

    @Operation(summary = "방 취소",
            description = "모집 미달·오생성 방을 CANCELLED로 못박는다(sticky). 이후 status 전이 배치·초대코드 입장에서 "
                    + "모두 제외된다. 이미 종료(ENDED)된 방은 400. SUPER·OPERATOR 전용.")
    @PostMapping("/rooms/{roomId}/cancel")
    public ApiResponse<Void> cancelRoom(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId) {
        adminRoomCommandService.cancelRoom(roomId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "초대코드 목록", description = "해당 방의 코드 이력(최신순). 없는 방은 404.")
    @GetMapping("/rooms/{roomId}/invites")
    public ApiResponse<List<AdminRoomInviteResponse>> invites(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId) {
        return ApiResponse.ok(adminRoomCommandService.getInvites(roomId));
    }

    @Operation(summary = "멤버 성별 부여",
            description = "초대코드 입장은 성별이 비어 있어(온보딩 미수집) 운영진이 신청폼 기준으로 채운다. "
                    + "MALE·FEMALE 외 값은 400. SUPER·OPERATOR 전용.")
    @PatchMapping("/rooms/{roomId}/members/{memberId}")
    public ApiResponse<AdminRoomMemberResponse> assignGender(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId,
            @Parameter(description = "방 멤버 id", example = "33") @PathVariable Long memberId,
            @RequestBody @Valid AssignGenderRequest request) {
        return ApiResponse.ok(adminRoomCommandService.assignGender(roomId, memberId, request.toCommand()));
    }
}

package org.son.haruharu.admin.domain.room.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.room.business.AdminRoomOrganizationService;
import org.son.haruharu.admin.domain.room.implement.AdminRoomGroup;
import org.son.haruharu.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminRoomOrganizationController {
    private final AdminRoomOrganizationService service;

    public AdminRoomOrganizationController(AdminRoomOrganizationService service) {
        this.service = service;
    }

    public record GroupRequest(@NotBlank @Size(max = 50) String name) {}

    public record AssignmentRequest(
            @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> roomIds,
            @Positive Long groupId,
            Boolean clearGroup,
            Boolean isTest) {}

    public record TypeRequest(@NotNull Boolean isTest) {}

    @GetMapping("/room-groups")
    public ApiResponse<PageResponse<AdminRoomGroup>> groups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.groups(page, size));
    }

    @PostMapping("/room-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminRoomGroup> create(@Valid @RequestBody GroupRequest body) {
        return ApiResponse.ok(service.createGroup(body.name()));
    }

    @PatchMapping("/rooms/organization")
    public ApiResponse<Void> assign(@Valid @RequestBody AssignmentRequest body) {
        service.assign(
                body.roomIds(),
                body.groupId(),
                Boolean.TRUE.equals(body.clearGroup()),
                body.isTest());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/rooms/{roomId}/type")
    public ApiResponse<Map<String, Object>> type(
            @PathVariable Long roomId, @Valid @RequestBody TypeRequest body) {
        service.assign(List.of(roomId), null, false, body.isTest());
        return ApiResponse.ok(Map.of("room_id", roomId, "is_test", body.isTest()));
    }
}

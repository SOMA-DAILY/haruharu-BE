package org.son.haruharu.admin.domain.room.application;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.admin.domain.room.application.dto.OperatorMissionRequest;
import org.son.haruharu.admin.domain.room.business.AdminOperatorMissionService;
import org.son.haruharu.admin.domain.room.business.dto.OperatorMissionPageResponse;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.mission.implement.OperatorMission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/rooms/{roomId}/operator-missions")
@RequiredArgsConstructor
public class AdminOperatorMissionController {
    private final AdminOperatorMissionService service;

    @GetMapping
    public ApiResponse<OperatorMissionPageResponse> history(@PathVariable Long roomId,
            @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.history(roomId, cursor, size));
    }
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse> create(@PathVariable Long roomId, @RequestBody @Valid OperatorMissionRequest request,
                                              Authentication authentication) {
        return ApiResponse.ok(service.create(roomId, (Long) authentication.getPrincipal(), request.toCommand()));
    }
    @PostMapping(value="/images", consumes="multipart/form-data")
    public ApiResponse<org.son.haruharu.domain.mission.business.dto.MissionImageResponse> upload(@PathVariable Long roomId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file, Authentication authentication) throws java.io.IOException {
        return ApiResponse.ok(service.upload(roomId,(Long) authentication.getPrincipal(),file.getBytes(),file.getContentType()));
    }
    @PutMapping("/{id}")
    public ApiResponse<org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse> update(@PathVariable Long roomId, @PathVariable Long id,
                                              @RequestBody @Valid OperatorMissionRequest request) {
        return ApiResponse.ok(service.update(roomId, id, request.toCommand()));
    }
    @PostMapping("/{id}/cancel")
    public ApiResponse<org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse> cancel(@PathVariable Long roomId, @PathVariable Long id) {
        return ApiResponse.ok(service.cancel(roomId, id));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse> delete(@PathVariable Long roomId, @PathVariable Long id) {
        return ApiResponse.ok(service.delete(roomId, id));
    }
}

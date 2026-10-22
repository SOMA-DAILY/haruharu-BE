package org.son.haruharu.domain.room.application;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.room.application.dto.ClaimInteractionGuideRequest;
import org.son.haruharu.domain.room.business.InteractionGuidanceService;
import org.son.haruharu.domain.room.business.dto.InteractionGuidanceResponse;
import org.son.haruharu.domain.room.business.dto.InteractionGuideClaimResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rooms/{roomId}/interaction-guidance")
public class InteractionGuidanceController {
    private final InteractionGuidanceService service;

    @GetMapping
    public ApiResponse<InteractionGuidanceResponse> get(@PathVariable Long roomId, Authentication auth) {
        return ApiResponse.ok(service.get(roomId, (Long) auth.getPrincipal()));
    }

    @PostMapping("/claim")
    public ApiResponse<InteractionGuideClaimResponse> claim(@PathVariable Long roomId,
            @Valid @RequestBody ClaimInteractionGuideRequest request, Authentication auth) {
        return ApiResponse.ok(service.claim(roomId, (Long) auth.getPrincipal(), request.toCommand()));
    }
}

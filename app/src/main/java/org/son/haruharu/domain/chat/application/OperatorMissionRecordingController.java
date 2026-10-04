package org.son.haruharu.domain.chat.application;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.chat.business.OperatorMissionRecordingService;
import org.son.haruharu.domain.chat.business.dto.OperatorMissionRecordingResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat-rooms/{chatRoomId}/messages/{messageId}/mission-recording")
public class OperatorMissionRecordingController {
    private final OperatorMissionRecordingService service;
    @GetMapping
    public ApiResponse<OperatorMissionRecordingResponse> get(@PathVariable Long chatRoomId,
            @PathVariable Long messageId, Authentication authentication) {
        return ApiResponse.ok(service.get(chatRoomId, messageId, (Long) authentication.getPrincipal()));
    }
}

package org.son.haruharu.domain.chat.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.chat.business.VideoReplyService;
import org.son.haruharu.domain.chat.business.dto.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class VideoReplyController {
    private final VideoReplyService service;

    public record ReplyRequest(
            @NotBlank @Size(max = 2000) String content,
            @NotBlank @Size(max = 36) String requestId) {}

    @PostMapping("/rooms/{roomId}/video-posts/{videoId}/reply")
    public ApiResponse<ChatMessageBroadcast> send(
            @PathVariable Long roomId,
            @PathVariable Long videoId,
            @Valid @RequestBody ReplyRequest request,
            Authentication auth) {
        return ApiResponse.ok(
                service.send(
                        roomId,
                        videoId,
                        (Long) auth.getPrincipal(),
                        request.content(),
                        request.requestId()));
    }

    @GetMapping("/chat-rooms/{chatId}/video-replies/{videoId}")
    public ApiResponse<VideoReplyPreview> chatPreview(
            @PathVariable Long chatId, @PathVariable Long videoId, Authentication auth) {
        return ApiResponse.ok(service.previewForChat(chatId, videoId, (Long) auth.getPrincipal()));
    }

    @GetMapping("/rooms/{roomId}/video-posts/{videoId}/reply")
    public ApiResponse<VideoReplyPreview> preview(
            @PathVariable Long roomId, @PathVariable Long videoId, Authentication auth) {
        return ApiResponse.ok(service.preview(roomId, videoId, (Long) auth.getPrincipal()));
    }
}

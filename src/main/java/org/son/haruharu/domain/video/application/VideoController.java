package org.son.haruharu.domain.video.application;

import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.video.application.dto.ReactionRequest;
import org.son.haruharu.domain.video.application.dto.VideoPresignRequest;
import org.son.haruharu.domain.video.application.dto.VideoUploadRequest;
import org.son.haruharu.domain.video.business.VideoReactionService;
import org.son.haruharu.domain.video.business.VideoService;
import org.son.haruharu.domain.video.business.dto.ReactionResponse;
import org.son.haruharu.domain.video.business.dto.VideoPostResponse;
import org.son.haruharu.domain.video.business.dto.VideoUploadUrlResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class VideoController {

    private final VideoService videoService;
    private final VideoReactionService videoReactionService;

    public VideoController(VideoService videoService, VideoReactionService videoReactionService) {
        this.videoService = videoService;
        this.videoReactionService = videoReactionService;
    }

    @PostMapping("/video-posts/presign")
    public ApiResponse<VideoUploadUrlResponse> presign(
            @Valid @RequestBody VideoPresignRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(videoService.createPresignUrl(userId, request.filename()));
    }

    @PostMapping("/rooms/{roomId}/video-posts")
    public ApiResponse<VideoPostResponse> upload(
            @PathVariable Long roomId, @Valid @RequestBody VideoUploadRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(videoService.upload(userId, roomId, request.toCommand()));
    }

    @DeleteMapping("/video-posts/{videoPostId}")
    public ApiResponse<Void> delete(@PathVariable UUID videoPostId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        videoService.delete(userId, videoPostId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/video-posts/{videoPostId}/reactions")
    public ApiResponse<ReactionResponse> react(
            @PathVariable UUID videoPostId, @Valid @RequestBody ReactionRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(videoReactionService.toggle(userId, videoPostId, request.reactionType()));
    }
}

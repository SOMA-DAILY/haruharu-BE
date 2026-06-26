package org.son.haruharu.domain.video.application;

import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.video.application.dto.VideoPresignRequest;
import org.son.haruharu.domain.video.business.VideoService;
import org.son.haruharu.domain.video.business.dto.VideoUploadUrlResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/video-posts/presign")
    public ApiResponse<VideoUploadUrlResponse> presign(
            @Valid @RequestBody VideoPresignRequest request, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(videoService.createPresignUrl(userId, request.filename()));
    }
}

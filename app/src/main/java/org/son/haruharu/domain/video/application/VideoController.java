package org.son.haruharu.domain.video.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "영상", description = "하루 영상 업로드 라이프사이클(presign 발급 → 업로드 → 삭제)과 영상 반응 API")
@RestController
@RequestMapping("/api/v1")
public class VideoController {

    private final VideoService videoService;
    private final VideoReactionService videoReactionService;

    public VideoController(VideoService videoService, VideoReactionService videoReactionService) {
        this.videoService = videoService;
        this.videoReactionService = videoReactionService;
    }

    @Operation(summary = "영상 업로드용 presigned URL 발급",
            description = "요청 body의 filename 확장자를 검증하고 비공개 경로(users/{id}/videos/)의 presigned PUT URL을 발급한다. "
                    + "응답으로 업로드에 사용할 URL을 반환하며, 클라이언트는 이 URL로 영상을 직접 PUT 업로드한다. "
                    + "지원하지 않는 확장자(mp4/mov/webm 외)면 400 VIDEO_INVALID_EXTENSION.")
    @PostMapping("/video-posts/presign")
    public ApiResponse<VideoUploadUrlResponse> presign(
            @Valid @RequestBody VideoPresignRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(videoService.createPresignUrl(userId, request.filename()));
    }

    @Operation(summary = "영상 업로드(현재 슬롯 제출)",
            description = "presign으로 올린 영상을 특정 방의 현재 슬롯에 제출한다. 슬롯은 서버가 방 시작 상대 정시(slot_start)로 자동 판정하므로 "
                    + "클라는 슬롯을 지정하지 않는다. body 필드는 video_url(본인 업로드 경로여야 함), thumbnail_url, caption(최대 20자). "
                    + "에러: 방 멤버가 아니면 인가 실패, 방 없음 ROOM_NOT_FOUND(404), 진행 중(ACTIVE)이 아닌 방 VIDEO_ROOM_NOT_ACTIVE(409), "
                    + "본인 경로가 아닌 video_url이면 VIDEO_INVALID_URL(400), 이미 현재 슬롯에 올렸으면 VIDEO_SLOT_ALREADY_UPLOADED(409).")
    @PostMapping("/rooms/{roomId}/video-posts")
    public ApiResponse<VideoPostResponse> upload(
            @Parameter(description = "영상을 올릴 방 id", example = "1") @PathVariable Long roomId,
            @Valid @RequestBody VideoUploadRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(videoService.upload(userId, roomId, request.toCommand()));
    }

    @Operation(summary = "영상 삭제(재촬영)",
            description = "본인이 올린 영상을 소프트 삭제한다(재촬영하려면 먼저 삭제 후 재업로드). "
                    + "에러: 존재하지 않거나 이미 삭제된 영상이면 VIDEO_404_NOT_FOUND(404), 본인 영상이 아니면 VIDEO_403_NOT_OWNER(403).")
    @DeleteMapping("/video-posts/{videoPostId}")
    public ApiResponse<Void> delete(
            @Parameter(description = "삭제할 영상 id", example = "10") @PathVariable Long videoPostId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        videoService.delete(userId, videoPostId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "영상 반응 토글",
            description = "타인 영상에 reaction_type(LIKE 👍/HEART ❤️/LOVE 😍/LAUGH 😂/WOW 😮/FIRE 🔥) 반응을 토글한다. "
                    + "유저당 영상당 1개: 없으면 생성, 같은 타입 재요청이면 해제, 다른 타입이면 교체한다. "
                    + "에러: 존재하지 않거나 삭제된 영상이면 VIDEO_404_NOT_FOUND(404), 자기 영상에 반응하면 CANNOT_REACT_OWN_VIDEO(400), "
                    + "해당 방 멤버가 아니면 인가 실패.")
    @PostMapping("/video-posts/{videoPostId}/reactions")
    public ApiResponse<ReactionResponse> react(
            @Parameter(description = "반응할 영상 id", example = "10") @PathVariable Long videoPostId,
            @Valid @RequestBody ReactionRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(videoReactionService.toggle(userId, videoPostId, request.reactionType()));
    }
}

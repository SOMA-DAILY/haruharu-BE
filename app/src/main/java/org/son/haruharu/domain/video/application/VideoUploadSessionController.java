package org.son.haruharu.domain.video.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.video.application.dto.VideoUploadAssetPresignRequest;
import org.son.haruharu.domain.video.application.dto.VideoUploadSessionReserveRequest;
import org.son.haruharu.domain.video.business.VideoUploadSessionService;
import org.son.haruharu.domain.video.business.dto.VideoPostResponse;
import org.son.haruharu.domain.video.business.dto.VideoUploadAssetResponse;
import org.son.haruharu.domain.video.business.dto.VideoUploadSessionResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "영상 업로드 세션", description = "기기에서 변환한 몰아보기·피드 영상과 썸네일을 한 세션으로 접수·업로드·게시하는 API")
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/video-upload-sessions")
public class VideoUploadSessionController {

    private final VideoUploadSessionService videoUploadSessionService;

    public VideoUploadSessionController(VideoUploadSessionService videoUploadSessionService) {
        this.videoUploadSessionService = videoUploadSessionService;
    }

    @Operation(summary = "업로드 접수",
            description = "현재 슬롯으로 업로드 세션을 접수한다. 슬롯은 접수 시점에 고정되고 세션은 2시간 뒤 만료된다. "
                    + "같은 job_id 재요청은 기존 세션을 그대로 돌려준다. "
                    + "같은 슬롯에 게시 전인 본인 세션이 있으면 그 세션을 취소하고 새 세션을 접수한다. "
                    + "에러: 진행 중이 아닌 방 VIDEO_ROOM_NOT_ACTIVE(409), 그 슬롯에 이미 게시물이 있으면 "
                    + "VIDEO_SLOT_ALREADY_UPLOADED(409), 같은 job_id에 다른 캡션이면 VIDEO_UPLOAD_UNAVAILABLE(409).")
    @PostMapping
    public ApiResponse<VideoUploadSessionResponse> reserve(
            @Parameter(description = "영상을 올릴 방 id", example = "1") @PathVariable Long roomId,
            @Valid @RequestBody VideoUploadSessionReserveRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(videoUploadSessionService.reserve(userId, roomId, request.jobId(), request.caption()));
    }

    @Operation(summary = "내 미완료 업로드 세션 목록",
            description = "이 방에서 본인이 접수하고 아직 게시하지 않은(pending) 세션을 슬롯 순으로 돌려준다. "
                    + "게시·취소·만료된 세션은 포함하지 않는다. job_id를 잃은 앱이 남은 접수를 찾아 게시를 마무리하는 용도다. "
                    + "에러: 방 멤버가 아니면 ROOM_403_NOT_MEMBER(403).")
    @GetMapping
    public ApiResponse<List<VideoUploadSessionResponse>> listPending(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(videoUploadSessionService.listPending(userId, roomId));
    }

    @Operation(summary = "업로드 세션 조회",
            description = "status는 pending·complete·cancelled·expired 중 하나다. "
                    + "에러: 본인 세션이 아니면 VIDEO_403_NOT_OWNER(403), 없는 세션이면 VIDEO_UPLOAD_UNAVAILABLE(409).")
    @GetMapping("/{sessionId}")
    public ApiResponse<VideoUploadSessionResponse> get(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId,
            @Parameter(description = "업로드 세션 id(job_id)") @PathVariable UUID sessionId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(videoUploadSessionService.get(userId, roomId, sessionId));
    }

    @Operation(summary = "파일별 presigned PUT URL 발급",
            description = "variant(viewer·feed·thumbnail)별 임시 경로의 PUT URL을 발급한다. "
                    + "이미 검증을 통과한 파일이면 presigned_url 없이 uploaded=true를 돌려준다. "
                    + "에러: 알 수 없는 variant·허용하지 않는 Content-Type이면 VIDEO_UPLOAD_INVALID_ASSET(400), "
                    + "만료·취소·게시된 세션이면 VIDEO_UPLOAD_UNAVAILABLE(409).")
    @PostMapping("/{sessionId}/assets/{variant}/presign")
    public ApiResponse<VideoUploadAssetResponse> presign(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId,
            @Parameter(description = "업로드 세션 id(job_id)") @PathVariable UUID sessionId,
            @Parameter(description = "파일 종류: viewer·feed·thumbnail", example = "feed") @PathVariable String variant,
            @Valid @RequestBody(required = false) VideoUploadAssetPresignRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String contentType = request == null ? null : request.contentType();
        return ApiResponse.ok(videoUploadSessionService.presign(userId, roomId, sessionId, variant, contentType));
    }

    @Operation(summary = "업로드 게시",
            description = "세 파일의 크기·Content-Type을 확인한 뒤 게시 경로로 복사하고 접수한 슬롯에 게시한다. "
                    + "이미 게시된 세션은 처음 게시 결과를 그대로 돌려준다. "
                    + "에러: 파일이 없거나 형식이 다르면 VIDEO_UPLOAD_INCOMPLETE(400), 만료·취소된 세션이면 "
                    + "VIDEO_UPLOAD_UNAVAILABLE(409), 그 슬롯에 이미 게시물이 있으면 VIDEO_SLOT_ALREADY_UPLOADED(409).")
    @PostMapping("/{sessionId}/complete")
    public ApiResponse<VideoPostResponse> complete(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId,
            @Parameter(description = "업로드 세션 id(job_id)") @PathVariable UUID sessionId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(videoUploadSessionService.complete(userId, roomId, sessionId));
    }

    @Operation(summary = "업로드 취소",
            description = "게시 전 세션을 취소한다. "
                    + "에러: 이미 게시된 세션이면 VIDEO_UPLOAD_UNAVAILABLE(409).")
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> cancel(
            @Parameter(description = "방 id", example = "1") @PathVariable Long roomId,
            @Parameter(description = "업로드 세션 id(job_id)") @PathVariable UUID sessionId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        videoUploadSessionService.cancel(userId, roomId, sessionId);
        return ApiResponse.ok(null);
    }
}

package org.son.haruharu.domain.video.application;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.video.business.VideoUploadSessionService;
import org.son.haruharu.domain.video.business.dto.VideoPostResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rooms/{roomId}/video-upload-sessions")
public class VideoUploadSessionController {
    private final VideoUploadSessionService service;
    public record ReserveRequest(@NotNull UUID jobId, @Size(max=20) String caption) {}
    public record PresignRequest(String contentType) {}
    @PostMapping
    public ApiResponse<VideoUploadSessionService.SessionResponse> reserve(@PathVariable Long roomId,
            @Valid @RequestBody ReserveRequest request, Authentication auth) {
        return ApiResponse.ok(service.reserve((Long)auth.getPrincipal(),roomId,request.jobId(),request.caption()));
    }
    @GetMapping("/{id}")
    public ApiResponse<VideoUploadSessionService.SessionResponse> get(@PathVariable Long roomId,@PathVariable UUID id,Authentication auth) {
        return ApiResponse.ok(service.get((Long)auth.getPrincipal(),roomId,id));
    }
    @PostMapping("/{id}/assets/{variant}/presign")
    public ApiResponse<VideoUploadSessionService.AssetResponse> presign(@PathVariable Long roomId,@PathVariable UUID id,@PathVariable String variant,
            @RequestBody(required=false) PresignRequest request,Authentication auth) {
        return ApiResponse.ok(service.presign((Long)auth.getPrincipal(),roomId,id,variant,request==null?null:request.contentType()));
    }
    @PostMapping("/{id}/complete")
    public ApiResponse<VideoPostResponse> complete(@PathVariable Long roomId,@PathVariable UUID id,Authentication auth) {
        return ApiResponse.ok(service.complete((Long)auth.getPrincipal(),roomId,id));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable Long roomId,@PathVariable UUID id,Authentication auth) {
        service.cancel((Long)auth.getPrincipal(),roomId,id);return ApiResponse.ok(null);
    }
}

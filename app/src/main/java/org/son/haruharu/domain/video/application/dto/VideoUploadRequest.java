package org.son.haruharu.domain.video.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.son.haruharu.domain.video.business.command.VideoUploadCommand;

public record VideoUploadRequest(
        @NotBlank String videoUrl,
        @Schema(description = "사용하지 않음(2026-07-12 확정) — null로 보낼 것. 미리보기는 video_url 첫 프레임을 클라이언트가 렌더링한다",
                nullable = true) String thumbnailUrl,
        @Size(max = 20) String caption,
        java.time.LocalDateTime expectedSlotStart
) {
    public VideoUploadRequest(String videoUrl, String thumbnailUrl, String caption) {
        this(videoUrl, thumbnailUrl, caption, null);
    }
    public VideoUploadCommand toCommand() {
        return new VideoUploadCommand(videoUrl, thumbnailUrl, caption, expectedSlotStart);
    }
}

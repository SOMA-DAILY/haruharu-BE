package org.son.haruharu.domain.video.business.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import org.son.haruharu.domain.video.implement.VideoUploadSession;

/** status: pending·complete·cancelled·expired. postId는 게시 완료 후에만 채워진다. */
public record VideoUploadSessionResponse(
        UUID id,
        LocalDateTime slotStart,
        LocalDateTime expiresAt,
        Long postId,
        String status
) {
    public static VideoUploadSessionResponse of(VideoUploadSession session, LocalDateTime now) {
        return new VideoUploadSessionResponse(session.id(), session.slotStart(), session.expiresAt(),
                session.postId(), session.status(now).value());
    }
}

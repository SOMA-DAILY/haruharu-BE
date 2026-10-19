package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.UUID;

import org.son.haruharu.domain.video.implement.exception.VideoUploadUnavailableException;
import org.son.haruharu.domain.video.implement.exception.InvalidVideoCaptionException;

/**
 * 기기 변환 영상의 업로드 세션. id는 클라이언트가 만든 작업 id라 재시도해도 같은 세션을 가리킨다.
 * 접수 시점의 슬롯과 게시 결과(postId)는 재시도로 바뀌지 않는다.
 */
public record VideoUploadSession(
        UUID id,
        Long userId,
        Long roomId,
        Long memberId,
        LocalDateTime slotStart,
        LocalDateTime expiresAt,
        String caption,
        Long postId,
        boolean isCancelled
) {
    private static final long TTL_HOURS = 2;
    private static final String STAGING_PREFIX = "users/video-upload-staging/";

    public static VideoUploadSession create(UUID id, Long userId, Long roomId, Long memberId,
                                            LocalDateTime slotStart, LocalDateTime now, String caption) {
        if (caption != null && caption.length() > VideoPost.MAX_CAPTION_LENGTH) {
            throw new InvalidVideoCaptionException();
        }
        return new VideoUploadSession(id, userId, roomId, memberId, slotStart, now.plusHours(TTL_HOURS),
                caption, null, false);
    }

    public boolean isCompleted() {
        return postId != null;
    }

    public boolean isOwnedBy(Long userId, Long roomId) {
        return this.userId.equals(userId) && this.roomId.equals(roomId);
    }

    public VideoUploadSessionStatus status(LocalDateTime now) {
        if (isCompleted()) {
            return VideoUploadSessionStatus.COMPLETE;
        }
        if (isCancelled) {
            return VideoUploadSessionStatus.CANCELLED;
        }
        return now.isBefore(expiresAt) ? VideoUploadSessionStatus.PENDING : VideoUploadSessionStatus.EXPIRED;
    }

    /** 파일 발급·게시는 대기 중인 세션에서만 가능하다. */
    public void requirePending(LocalDateTime now) {
        if (status(now) != VideoUploadSessionStatus.PENDING) {
            throw new VideoUploadUnavailableException();
        }
    }

    public VideoUploadSession completed(Long postId) {
        return new VideoUploadSession(id, userId, roomId, memberId, slotStart, expiresAt, caption, postId, false);
    }

    public VideoUploadSession cancelled() {
        return new VideoUploadSession(id, userId, roomId, memberId, slotStart, expiresAt, caption, postId, true);
    }

    /** 클라이언트가 PUT하는 임시 경로. S3 수명주기 규칙이 공통 prefix로 만료시킨다. */
    public String stagingKey(VideoUploadVariant variant) {
        return STAGING_PREFIX + userId + "/videos/" + id + "/" + variant.filename();
    }

    /** 검증 후 서버가 복사하는 게시 경로. 이 경로로는 PUT URL을 발급하지 않는다. */
    public String publishedKey(VideoUploadVariant variant) {
        return "users/" + userId + "/videos/" + id + "/published/" + variant.filename();
    }
}

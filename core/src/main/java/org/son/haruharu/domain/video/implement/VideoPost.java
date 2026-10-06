package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;

/**
 * 일상 영상 업로드. 슬롯은 {@code slotStart}(room.starts_at 상대 정시, {@link SlotClock})로 식별한다.
 * missionId가 있으면 미션 영상(미션 비활성화 동안 항상 null).
 */
public record VideoPost(
        Long id,
        Long roomMemberId,
        LocalDateTime slotStart,
        Long missionId,
        String caption,
        String videoUrl,
        String thumbnailUrl,
        boolean isHidden,
        String hiddenReason,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        String feedVideoUrl
) {
    /** 피드 전용 영상이 없는 게시물(레거시 업로드). 피드는 videoUrl을 그대로 쓴다. */
    public VideoPost(Long id, Long roomMemberId, LocalDateTime slotStart, Long missionId,
                     String caption, String videoUrl, String thumbnailUrl, boolean isHidden,
                     String hiddenReason, LocalDateTime deletedAt, LocalDateTime createdAt) {
        this(id, roomMemberId, slotStart, missionId, caption, videoUrl, thumbnailUrl,
                isHidden, hiddenReason, deletedAt, createdAt, null);
    }

    public static VideoPost create(Long roomMemberId, LocalDateTime slotStart, Long missionId,
                                   String caption, String videoUrl, String thumbnailUrl,
                                   LocalDateTime createdAt) {
        return new VideoPost(null, roomMemberId, slotStart, missionId, caption,
                videoUrl, thumbnailUrl, false, null, null, createdAt);
    }

    public VideoPost withFeedVideoUrl(String url) {
        return new VideoPost(id, roomMemberId, slotStart, missionId, caption,
                videoUrl, thumbnailUrl, isHidden, hiddenReason, deletedAt, createdAt, url);
    }

    public boolean isMissionVideo() {
        return missionId != null;
    }

    /** 소프트삭제(재촬영). 새 인스턴스를 반환. */
    public VideoPost deletedAt(LocalDateTime when) {
        return new VideoPost(id, roomMemberId, slotStart, missionId, caption,
                videoUrl, thumbnailUrl, isHidden, hiddenReason, when, createdAt, feedVideoUrl);
    }
}

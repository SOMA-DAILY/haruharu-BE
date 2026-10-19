package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;

import org.son.haruharu.domain.video.implement.exception.InvalidVideoCaptionException;

/**
 * 일상 영상 업로드. 슬롯은 {@code slotStart}(room.starts_at 상대 정시, {@link SlotClock})로 식별한다.
 * missionId는 과거 데이터 조회 호환용이다. 자동 미션 폐기 후 신규 영상은 null을 저장한다.
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
    public static final int MAX_CAPTION_LENGTH = 100;

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
        return new VideoPost(null, roomMemberId, slotStart, missionId, normalizeCaption(caption),
                videoUrl, thumbnailUrl, false, null, null, createdAt);
    }

    public VideoPost withFeedVideoUrl(String url) {
        return new VideoPost(id, roomMemberId, slotStart, missionId, caption,
                videoUrl, thumbnailUrl, isHidden, hiddenReason, deletedAt, createdAt, url);
    }

    public boolean isMissionVideo() {
        return missionId != null;
    }

    /** 캡션만 바꾼 새 인스턴스. 빈 문구는 캡션 제거(null)다. */
    public VideoPost withCaption(String newCaption) {
        return new VideoPost(id, roomMemberId, slotStart, missionId, normalizeCaption(newCaption),
                videoUrl, thumbnailUrl, isHidden, hiddenReason, deletedAt, createdAt, feedVideoUrl);
    }

    private static String normalizeCaption(String caption) {
        if (caption == null) {
            return null;
        }
        if (caption.length() > MAX_CAPTION_LENGTH) {
            throw new InvalidVideoCaptionException();
        }
        String stripped = caption.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** 소프트삭제(재촬영). 새 인스턴스를 반환. */
    public VideoPost deletedAt(LocalDateTime when) {
        return new VideoPost(id, roomMemberId, slotStart, missionId, caption,
                videoUrl, thumbnailUrl, isHidden, hiddenReason, when, createdAt, feedVideoUrl);
    }
}

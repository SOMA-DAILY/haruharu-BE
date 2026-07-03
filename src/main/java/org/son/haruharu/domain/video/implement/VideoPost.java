package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/** 일상 영상 업로드. 슬롯은 (dayNumber, slotIndex) 자연키로 참조. missionId가 있으면 미션 영상(열쇠 지급 대상). */
public record VideoPost(
        UUID id,
        UUID roomMemberId,
        int dayNumber,
        int slotIndex,
        Long missionId,
        String caption,
        String videoUrl,
        String thumbnailUrl,
        boolean isHidden,
        String hiddenReason,
        LocalDateTime deletedAt,
        LocalDateTime createdAt
) {
    public static VideoPost create(UUID roomMemberId, int dayNumber, int slotIndex, Long missionId,
                                   String caption, String videoUrl, String thumbnailUrl,
                                   LocalDateTime createdAt) {
        return new VideoPost(null, roomMemberId, dayNumber, slotIndex, missionId, caption,
                videoUrl, thumbnailUrl, false, null, null, createdAt);
    }

    public boolean isMissionVideo() {
        return missionId != null;
    }

    /** 소프트삭제(재촬영). 새 인스턴스를 반환. */
    public VideoPost deletedAt(LocalDateTime when) {
        return new VideoPost(id, roomMemberId, dayNumber, slotIndex, missionId, caption,
                videoUrl, thumbnailUrl, isHidden, hiddenReason, when, createdAt);
    }
}

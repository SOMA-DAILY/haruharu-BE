package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;

/**
 * 일상 영상 업로드. 슬롯은 {@code slotStart}(room.starts_at 상대 정시, {@link SlotClock})로 식별한다.
 * dayNumber는 파트너 프로필 영상 일자별 그룹핑용으로만 남아 있다(L2에서 slot_start 파생으로 대체 예정) — 슬롯 로직엔 쓰지 않는다.
 * missionId가 있으면 미션 영상(미션 비활성화 동안 항상 null).
 */
public record VideoPost(
        Long id,
        Long roomMemberId,
        int dayNumber,
        LocalDateTime slotStart,
        Long missionId,
        String caption,
        String videoUrl,
        String thumbnailUrl,
        boolean isHidden,
        String hiddenReason,
        LocalDateTime deletedAt,
        LocalDateTime createdAt
) {
    public static VideoPost create(Long roomMemberId, int dayNumber, LocalDateTime slotStart, Long missionId,
                                   String caption, String videoUrl, String thumbnailUrl,
                                   LocalDateTime createdAt) {
        return new VideoPost(null, roomMemberId, dayNumber, slotStart, missionId, caption,
                videoUrl, thumbnailUrl, false, null, null, createdAt);
    }

    public boolean isMissionVideo() {
        return missionId != null;
    }

    /** 소프트삭제(재촬영). 새 인스턴스를 반환. */
    public VideoPost deletedAt(LocalDateTime when) {
        return new VideoPost(id, roomMemberId, dayNumber, slotStart, missionId, caption,
                videoUrl, thumbnailUrl, isHidden, hiddenReason, when, createdAt);
    }
}

package org.son.haruharu.domain.video.business.dto;

import java.time.LocalDateTime;

import org.son.haruharu.domain.video.implement.VideoPost;

/**
 * 업로드 결과. MVP: 미션 비활성화로 missionId=null 고정. 세션 재화(열쇠) 제거됨.
 */
public record VideoPostResponse(
        Long videoPostId,
        int dayNumber,
        int slotIndex,
        Long missionId,
        String videoUrl,
        String thumbnailUrl,
        String caption,
        LocalDateTime createdAt
) {
    public static VideoPostResponse of(VideoPost post) {
        return new VideoPostResponse(
                post.id(), post.dayNumber(), post.slotIndex(), post.missionId(), post.videoUrl(),
                post.thumbnailUrl(), post.caption(), post.createdAt());
    }
}

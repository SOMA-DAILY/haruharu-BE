package org.son.haruharu.domain.video.business.dto;

import org.son.haruharu.domain.video.implement.VideoPost;

import java.time.LocalDateTime;
import java.util.UUID;

/** 업로드 결과. grantedPoints>0면 미션 영상으로 열쇠가 지급됨, keyBalance는 지급 후 잔액. */
public record VideoPostResponse(
        UUID videoPostId,
        int dayNumber,
        int slotIndex,
        UUID missionId,
        String videoUrl,
        String thumbnailUrl,
        String caption,
        int grantedPoints,
        int keyBalance,
        LocalDateTime createdAt
) {
    public static VideoPostResponse of(VideoPost post, int grantedPoints, int keyBalance) {
        return new VideoPostResponse(
                post.id(), post.dayNumber(), post.slotIndex(), post.missionId(), post.videoUrl(),
                post.thumbnailUrl(), post.caption(), grantedPoints, keyBalance, post.createdAt());
    }
}

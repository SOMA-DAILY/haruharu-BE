package org.son.haruharu.domain.video.business.dto;

import org.son.haruharu.domain.video.implement.VideoPost;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 업로드 결과. MVP: 미션 비활성화로 missionId=null·grantedPoints=0 고정, keyBalance는 현재 열쇠 잔액.
 * 미션 재개 시 지급 로직 복원 후 grantedPoints/keyBalance가 의미를 갖는다.
 */
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

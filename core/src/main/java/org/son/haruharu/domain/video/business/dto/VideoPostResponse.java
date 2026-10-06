package org.son.haruharu.domain.video.business.dto;

import java.time.LocalDateTime;

import org.son.haruharu.domain.video.implement.VideoPost;

/**
 * 업로드 결과. missionId는 과거 데이터 호환용이며 신규 영상은 null이다.
 */
public record VideoPostResponse(
        Long videoPostId,
        LocalDateTime slotStart,
        Long missionId,
        String videoUrl,
        String thumbnailUrl,
        String caption,
        LocalDateTime createdAt
) {
    /** videoUrl·thumbnailUrl은 비공개 원본이 아니라 재생 가능한 presigned GET URL을 전달한다(#162). */
    public static VideoPostResponse of(VideoPost post, String videoUrl, String thumbnailUrl) {
        return new VideoPostResponse(
                post.id(), post.slotStart(), post.missionId(), videoUrl,
                thumbnailUrl, post.caption(), post.createdAt());
    }
}

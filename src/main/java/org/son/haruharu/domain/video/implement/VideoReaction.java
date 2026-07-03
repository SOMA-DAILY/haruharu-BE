package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/** 영상 반응(좋아요/궁금해요/공감). 유저당 영상당 1행. */
public record VideoReaction(
        UUID id,
        UUID videoPostId,
        Long userId,
        String reactionType,
        LocalDateTime createdAt
) {
    public static VideoReaction create(UUID videoPostId, Long userId, String reactionType, LocalDateTime createdAt) {
        return new VideoReaction(null, videoPostId, userId, reactionType, createdAt);
    }

    /** 다른 반응으로 교체(같은 행 유지). */
    public VideoReaction withType(String newType) {
        return new VideoReaction(id, videoPostId, userId, newType, createdAt);
    }
}

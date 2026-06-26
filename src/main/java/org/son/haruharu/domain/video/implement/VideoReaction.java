package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/** 영상 반응(좋아요/궁금해요/공감). 토글 로직은 #40. */
public record VideoReaction(
        UUID id,
        UUID videoPostId,
        UUID userId,
        String reactionType,
        LocalDateTime createdAt
) {}

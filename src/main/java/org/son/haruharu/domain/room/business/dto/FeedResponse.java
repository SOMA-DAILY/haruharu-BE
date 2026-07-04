package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * day N 영상 피드. locked=true면 내가 해당 Day에 업로드하지 않아 타 멤버 영상이 잠긴 상태(posts 빈 목록).
 * 반응 수 집계는 #40 범위 밖(별도 reactions 엔드포인트). JSON은 전역 SNAKE_CASE.
 */
public record FeedResponse(boolean locked, List<FeedItem> posts) {

    public record FeedItem(
            Long videoPostId,
            Long roomMemberId,
            boolean mine,
            String videoUrl,
            String thumbnailUrl,
            String caption,
            LocalDateTime createdAt
    ) {}
}

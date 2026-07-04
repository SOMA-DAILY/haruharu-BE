package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * day N 영상 피드. 잠금은 슬롯(시간대) 단위: slots의 각 항목이 그 슬롯 잠금 여부를 나타내고,
 * posts는 내가 업로드해 열람 가능한 슬롯의 영상만 담는다(스토리형 좌/우 슬롯 이동 UI 대응).
 * 반응 집계는 #58 범위. JSON은 전역 SNAKE_CASE.
 */
public record FeedResponse(List<SlotStatus> slots, List<FeedItem> posts) {

    public record SlotStatus(int slotIndex, boolean locked) {}

    public record FeedItem(
            Long videoPostId,
            Long roomMemberId,
            boolean mine,
            int slotIndex,
            String videoUrl,
            String thumbnailUrl,
            String caption,
            LocalDateTime createdAt
    ) {}
}

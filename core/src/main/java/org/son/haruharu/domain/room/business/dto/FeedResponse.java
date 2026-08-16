package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * day N 영상 피드. 잠금은 슬롯(시간대) 단위: slots의 각 항목이 그 슬롯 잠금 여부를 나타내고,
 * posts는 내가 업로드해 열람 가능한 슬롯의 영상만 담는다(스토리형 좌/우 슬롯 이동 UI 대응).
 * 각 post는 반응 이모지별 집계(reactionCounts, 0인 타입은 생략·enum 순서)와 내 반응(myReaction, 없으면 null)을 함께 노출한다.
 * userId는 작성자 신고(reported_user_id) 연결용. JSON은 전역 SNAKE_CASE.
 */
public record FeedResponse(List<SlotStatus> slots, List<FeedItem> posts) {

    public record SlotStatus(int slotIndex, boolean locked) {}

    public record FeedItem(
            Long videoPostId,
            Long roomMemberId,
            Long userId,
            boolean mine,
            int slotIndex,
            String videoUrl,
            String thumbnailUrl,
            String caption,
            LocalDateTime createdAt,
            List<ReactionTally> reactionCounts,
            String myReaction
    ) {}

    /** 반응 이모지 한 종의 집계. type=ReactionType.name(), emoji=표시 이모지, count=반응 수(>0). */
    public record ReactionTally(String type, String emoji, long count) {}
}

package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 현재 노출 중인 슬롯 창(slot_start 기준 최근 3시간)의 영상 피드. slots는 공개 가능한 영상이 있는 시간대와
 * ACTIVE 현재 촬영 시간대를 담는다(스토리형 좌/우 슬롯 이동 UI 대응). locked는 하위 호환 필드이며 항상 false다.
 * 각 post는 반응 이모지별 집계(reactionCounts, 0인 타입은 생략·enum 순서)와 내 반응(myReaction, 없으면 null)을 함께 노출한다.
 * userId는 작성자 신고(reported_user_id) 연결용. JSON은 전역 SNAKE_CASE.
 * nextCursor는 빈 시간대를 포함한 원본 시간축의 다음 과거 조회 커서다. 다음 요청의 {@code before} 파라미터로 넣으며,
 * 방 시작에 도달하면 null(#350).
 */
public record FeedResponse(List<SlotStatus> slots, List<FeedItem> posts, LocalDateTime nextCursor) {

    /** locked는 하위 호환 필드로 항상 false. current=지금 촬영 가능한 슬롯(dots의 촬영 CTA 대상). */
    public record SlotStatus(LocalDateTime slotStart, boolean locked, boolean current) {}

    public record FeedItem(
            Long videoPostId,
            Long roomMemberId,
            Long userId,
            boolean mine,
            LocalDateTime slotStart,
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

package org.son.haruharu.domain.chat.business.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;

/**
 * 매칭 상대 프로필 카드. 매칭쌍이므로 프로필 항목·가치관·나이는 전부 공개되고,
 * 1~3일차 영상을 day별로 함께 내린다(상호슬롯잠금 미적용). videoUrl은 정적 공개 URL이라
 * 클라가 day 탭 클릭 시 lazy 재생한다(추가 호출 없음).
 * profile은 room 도메인의 MemberProfileResponse(매칭 시 전항목 공개)를 재사용한다.
 */
public record PartnerProfileResponse(
        Long userId,
        MemberProfileResponse profile,
        Map<Integer, List<PartnerVideo>> videosByDay
) {
    public record PartnerVideo(
            Long videoPostId,
            int slotIndex,
            String videoUrl,
            String thumbnailUrl,
            String caption,
            LocalDateTime createdAt
    ) {}
}

package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;

/**
 * 매칭 상대 프로필 카드. 매칭쌍이므로 프로필 항목·가치관·나이는 전부 공개되고,
 * 상대 영상을 slot_start 오름차순 리스트로 함께 내린다(상호슬롯잠금 미적용). videoUrl은 비공개 원본이 아니라
 * 재생 가능한 단기 presigned GET URL이며(#162), 만료 시 클라가 이 응답을 재요청해 갱신한다.
 * profile은 room 도메인의 MemberProfileResponse(매칭 시 전항목 공개)를 재사용한다.
 */
public record PartnerProfileResponse(
        @Schema(description = "상대 유저 id", example = "102") Long userId,
        @Schema(description = "상대 프로필(매칭쌍은 전 항목 공개)") MemberProfileResponse profile,
        @Schema(description = "상대 영상 목록(slot_start 오름차순)") List<PartnerVideo> videos
) {
    public record PartnerVideo(
            @Schema(description = "영상 id", example = "1001") Long videoPostId,
            @Schema(description = "업로드 슬롯 시작 시각(slot_start)") LocalDateTime slotStart,
            @Schema(description = "재생용 presigned GET URL(단기 만료, 만료 시 재조회)") String videoUrl,
            @Schema(description = "썸네일 presigned GET URL(없으면 null)") String thumbnailUrl,
            @Schema(description = "캡션") String caption,
            @Schema(description = "업로드 시각") LocalDateTime createdAt
    ) {}
}

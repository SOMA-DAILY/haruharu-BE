package org.son.haruharu.domain.room.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 내 매칭 결과. 상대 방 멤버 id·성사 시각에 더해, 결과 화면에서 바로 채팅으로 직행할 수 있도록 자동 생성 채팅방 id와 상대 프로필 요약을 포함한다. */
public record MatchResultResponse(
        @Schema(description = "매칭 상대의 방 멤버 id", example = "30") Long partnerMemberId,
        @Schema(description = "매칭 성사 시각") LocalDateTime matchedAt,
        @Schema(description = "성사 시 자동 생성된 채팅방 id(아직 없으면 null)", example = "77", nullable = true) Long chatRoomId,
        @Schema(description = "상대 프로필 요약") PartnerSummary partner
) {
    /** 결과 화면 표시용 상대 요약(닉네임·대표사진). */
    public record PartnerSummary(
            @Schema(description = "상대 닉네임", example = "하늘") String nickname,
            @Schema(description = "상대 대표사진 URL(없으면 null)",
                    example = "https://cdn.haruharu.app/profile/a.jpg", nullable = true) String profileImageUrl
    ) {}
}

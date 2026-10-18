package org.son.haruharu.domain.cohort.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.room.implement.RoomMember;

/** 방에 넣은 신청자 한 명(#516). */
public record AssignedMemberResponse(
        @Schema(description = "신청 id", example = "12") Long applicationId,
        @Schema(description = "유저 id", example = "901") Long userId,
        @Schema(description = "방 멤버 id", example = "33") Long memberId,
        @Schema(description = "성별(PASS)", example = "FEMALE") String gender,
        @Schema(description = "방 세션 가명", example = "유진") String pseudonym
) {
    public static AssignedMemberResponse from(RoomMember member) {
        return new AssignedMemberResponse(member.applicationId(), member.userId(), member.id(), member.gender(),
                member.pseudonym());
    }
}

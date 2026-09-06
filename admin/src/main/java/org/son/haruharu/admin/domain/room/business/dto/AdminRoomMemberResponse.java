package org.son.haruharu.admin.domain.room.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.room.implement.RoomMember;

/** 성별 부여 결과(#318). 갱신된 값만 확인하면 되므로 프로필 enrich 없이 최소 필드로 내린다. 가명은 성별 부여 시 백필된다(#393·#400). */
public record AdminRoomMemberResponse(
        @Schema(description = "방 멤버 id", example = "33") Long memberId,
        @Schema(description = "유저 id", example = "901") Long userId,
        @Schema(description = "성별", example = "FEMALE") String gender,
        @Schema(description = "방 세션 가명(성별 부여 시 백필, #393)", example = "유진", nullable = true) String pseudonym
) {
    public static AdminRoomMemberResponse from(RoomMember member) {
        return new AdminRoomMemberResponse(member.id(), member.userId(), member.gender(), member.pseudonym());
    }
}

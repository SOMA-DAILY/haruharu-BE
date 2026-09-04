package org.son.haruharu.admin.domain.room.business.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteStatus;

/** 초대코드 행(#318). maxUses는 실제 방 정원(null이면 무제한), usedCount는 코드별 usage 메타데이터다. */
public record AdminRoomInviteResponse(
        @Schema(description = "코드 id", example = "5") Long inviteId,
        @Schema(description = "초대코드", example = "K7M2QX") String code,
        @Schema(description = "코드 상태", example = "ACTIVE") RoomInviteStatus status,
        @Schema(description = "관리자가 정하는 실제 방 정원(null이면 무제한)", example = "6", nullable = true) Integer maxUses,
        @Schema(description = "이 코드로 새로 생성된 membership 수", example = "2") int usedCount,
        @Schema(description = "만료 시각(null이면 무기한)", nullable = true) LocalDateTime expiresAt,
        @Schema(description = "발급 주체", example = "admin:1") String createdBy
) {
    public static AdminRoomInviteResponse from(RoomInvite invite) {
        return new AdminRoomInviteResponse(invite.id(), invite.code(), invite.status(),
                invite.maxUses(), invite.usedCount(), invite.expiresAt(), invite.createdBy());
    }
}

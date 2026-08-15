package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

/**
 * 방 입장 초대코드(#312). first-class 행으로 만료·다중사용·자동생성 확장에 대비한다([[mvp-pivot-invite-code]]).
 * 정원은 maxUses로 표현한다(MVP: 방당 ACTIVE 코드 1개, maxUses=6=3:3). createdBy 는 admin id 또는 "SYSTEM".
 */
public record RoomInvite(
        Long id,
        Long roomId,
        String code,
        RoomInviteStatus status,
        Integer maxUses,
        int usedCount,
        LocalDateTime expiresAt,
        String createdBy
) {
    /** 코드 발급: ACTIVE·usedCount=0. */
    public static RoomInvite issue(Long roomId, String code, Integer maxUses,
                                   LocalDateTime expiresAt, String createdBy) {
        return new RoomInvite(null, roomId, code, RoomInviteStatus.ACTIVE, maxUses, 0, expiresAt, createdBy);
    }

    /** 지금 사용 가능한가: ACTIVE + 미만료 + 정원(maxUses) 미초과. */
    public boolean isUsable(LocalDateTime now) {
        if (status != RoomInviteStatus.ACTIVE) {
            return false;
        }
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            return false;
        }
        return maxUses == null || usedCount < maxUses;
    }

    /** 사용 1회 증가(입장 성공 시). */
    public RoomInvite incrementUsed() {
        return new RoomInvite(id, roomId, code, status, maxUses, usedCount + 1, expiresAt, createdBy);
    }
}

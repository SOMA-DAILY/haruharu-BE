package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

import org.son.haruharu.domain.room.implement.exception.InvalidRoomInviteMaxUsesException;

/**
 * 방 입장 초대코드(#312). first-class 행으로 만료·다중사용·자동생성 확장에 대비한다([[mvp-pivot-invite-code]]).
 * maxUses는 초대코드로 들어오는 인원(웹 몫)의 정원이며 null이면 무제한이다. 앱 신청자 배정 인원은 세지 않는다(#513).
 * 방 전체 성별 정원은 rooms.male/female_capacity 가 따로 갖는다. usedCount는 코드별 usage 메타데이터다.
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
        if (maxUses != null && maxUses <= 0) {
            throw new InvalidRoomInviteMaxUsesException();
        }
        return new RoomInvite(null, roomId, code, RoomInviteStatus.ACTIVE, maxUses, 0, expiresAt, createdBy);
    }

    /** 지금 lifecycle상 사용 가능한가: ACTIVE + 미만료. 정원은 room lock 아래 active membership으로 따로 판정한다. */
    public boolean isUsable(LocalDateTime now) {
        if (status != RoomInviteStatus.ACTIVE) {
            return false;
        }
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            return false;
        }
        return true;
    }

    /** 방에 초대코드로 들어온 멤버 수 기준으로 새 멤버 한 명을 받을 수 있는지 판단한다. null은 무제한이다. */
    public boolean hasCapacity(long inviteMemberCount) {
        return maxUses == null || inviteMemberCount < maxUses;
    }

    /** 사용 1회 증가(입장 성공 시). */
    public RoomInvite incrementUsed() {
        return new RoomInvite(id, roomId, code, status, maxUses, usedCount + 1, expiresAt, createdBy);
    }

    /**
     * 코드 만료(#318). 재발급 시 기존 ACTIVE 코드를 닫아 방당 ACTIVE 코드 1개를 유지한다.
     * 코드 문자열은 남겨 둔다 — 이미 배포된 코드로 들어온 입장 시도를 "만료"로 구분해 응답하기 위함.
     */
    public RoomInvite expired() {
        return new RoomInvite(id, roomId, code, RoomInviteStatus.EXPIRED, maxUses, usedCount, expiresAt, createdBy);
    }
}

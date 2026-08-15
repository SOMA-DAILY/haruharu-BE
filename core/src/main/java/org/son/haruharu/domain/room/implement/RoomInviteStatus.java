package org.son.haruharu.domain.room.implement;

/**
 * 초대코드 상태(#312).
 * ACTIVE   = 사용 가능.
 * DISABLED = 운영진이 비활성화.
 * EXPIRED  = 만료(정책상 상태값; 실만료 판정은 expiresAt 시각으로도 한다).
 */
public enum RoomInviteStatus {
    ACTIVE,
    DISABLED,
    EXPIRED
}

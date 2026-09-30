package org.son.haruharu.domain.roomschedule.implement;

/**
 * 편성 행 상태(#457). 어드민은 SCHEDULED 에서만 바꾼다(수정·지금 보내기·취소). 잡이 SCHEDULED → SENDING(claim) → SENT/FAILED.
 */
public enum RoomScheduleStatus {
    SCHEDULED,
    SENDING,
    SENT,
    CANCELED,
    FAILED
}

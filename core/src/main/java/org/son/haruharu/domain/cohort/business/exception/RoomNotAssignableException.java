package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class RoomNotAssignableException extends ConflictException {
    public RoomNotAssignableException() {
        super("ROOM_NOT_ASSIGNABLE", "기수가 정해진, 끝나거나 취소되지 않은 방에만 신청자를 넣을 수 있습니다.");
    }
}

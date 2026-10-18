package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class RoomGenderCapacityExceededException extends ConflictException {
    public RoomGenderCapacityExceededException() {
        super("ROOM_GENDER_CAPACITY_EXCEEDED", "방의 성별 정원을 넘습니다.");
    }
}

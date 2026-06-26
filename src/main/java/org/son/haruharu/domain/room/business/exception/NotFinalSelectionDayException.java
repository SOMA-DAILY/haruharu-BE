package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class NotFinalSelectionDayException extends ConflictException {

    public NotFinalSelectionDayException() {
        super("ROOM_409_NOT_FINAL_DAY", "아직 최종선택을 할 수 있는 날(Day3)이 아닙니다.");
    }
}

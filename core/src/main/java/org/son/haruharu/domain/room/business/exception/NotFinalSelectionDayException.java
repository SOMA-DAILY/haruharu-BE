package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class NotFinalSelectionDayException extends ConflictException {

    public NotFinalSelectionDayException() {
        super("ROOM_409_NOT_FINAL_DAY", "아직 최종선택 시간이 아닙니다. 마지막 날 슬롯 종료 후 가능합니다.");
    }
}

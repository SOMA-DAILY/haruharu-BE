package org.son.haruharu.domain.video.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class SlotNotInCurrentDayException extends BadRequestException {

    public SlotNotInCurrentDayException() {
        super("VIDEO_SLOT_NOT_TODAY", "오늘(currentDay)의 슬롯이 아닙니다.");
    }
}

package org.son.haruharu.domain.roomschedule.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class ScheduleItemNotFoundException extends NotFoundException {
    public ScheduleItemNotFoundException() {
        super("SCHEDULE_ITEM_NOT_FOUND", "존재하지 않는 편성입니다.");
    }
}

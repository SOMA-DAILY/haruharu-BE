package org.son.haruharu.domain.roomschedule.business.exception;

import org.son.haruharu.common.exception.ConflictException;

/** SCHEDULED 가 아닌 행을 바꾸려 함(#457). 어드민 화면이 토스트에 message 를 그대로 보여준다. */
public class ScheduleItemNotEditableException extends ConflictException {
    public ScheduleItemNotEditableException() {
        super("SCHEDULE_ITEM_NOT_EDITABLE", "이미 보냈거나 취소된 편성이에요");
    }
}

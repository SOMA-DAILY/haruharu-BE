package org.son.haruharu.domain.roomschedule.business.exception;

import org.son.haruharu.common.exception.ConflictException;

/** 실행 중 최신 방/편성 상태가 달라졌을 때 저장을 롤백한다. */
public class ScheduleDispatchRejectedException extends ConflictException {
    public ScheduleDispatchRejectedException(String reason) {
        super(reason, "현재 방 또는 편성 상태에서는 실행할 수 없습니다.");
    }
}

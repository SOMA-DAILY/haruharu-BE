package org.son.haruharu.devsupport.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidDevDayException extends BadRequestException {
    public InvalidDevDayException() {
        super("DEV_INVALID_DAY", "day는 1~totalDays 범위여야 합니다.");
    }
}

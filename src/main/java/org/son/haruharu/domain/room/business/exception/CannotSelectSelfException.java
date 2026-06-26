package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class CannotSelectSelfException extends BadRequestException {

    public CannotSelectSelfException() {
        super("ROOM_400_CANNOT_SELECT_SELF", "자기 자신은 최종선택할 수 없습니다.");
    }
}

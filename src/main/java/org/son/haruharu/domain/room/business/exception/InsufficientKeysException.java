package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InsufficientKeysException extends BadRequestException {

    public InsufficientKeysException() {
        super("ROOM_INSUFFICIENT_KEYS", "열쇠가 부족하여 해금할 수 없습니다.");
    }
}

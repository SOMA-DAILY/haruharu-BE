package org.son.haruharu.devsupport.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class DevRoomNotActiveException extends BadRequestException {
    public DevRoomNotActiveException() {
        super("DEV_ROOM_NOT_ACTIVE", "ACTIVE 상태(시작된) 방에만 적용할 수 있습니다.");
    }
}

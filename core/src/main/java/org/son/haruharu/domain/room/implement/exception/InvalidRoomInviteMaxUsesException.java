package org.son.haruharu.domain.room.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

/** 초대코드 발급 정원은 null(무제한) 또는 양수여야 한다. */
public class InvalidRoomInviteMaxUsesException extends BadRequestException {

    public InvalidRoomInviteMaxUsesException() {
        super("ROOM_INVITE_MAX_USES_INVALID", "초대코드 정원은 양수 또는 null(무제한)이어야 합니다.");
    }
}

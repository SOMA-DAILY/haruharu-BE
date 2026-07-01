package org.son.haruharu.domain.room.implement.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class NotRoomMemberException extends ForbiddenException {

    public NotRoomMemberException() {
        super("ROOM_403_NOT_MEMBER", "해당 소개팅방의 참여자가 아닙니다.");
    }
}

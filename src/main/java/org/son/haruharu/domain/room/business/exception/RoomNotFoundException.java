package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class RoomNotFoundException extends NotFoundException {

    public RoomNotFoundException() {
        super("ROOM_404_NOT_FOUND", "참여중인 소개팅방이 없습니다.");
    }
}

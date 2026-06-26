package org.son.haruharu.domain.room.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class RoomMemberNotFoundException extends NotFoundException {

    public RoomMemberNotFoundException() {
        super("ROOM_MEMBER_404_NOT_FOUND", "존재하지 않는 방 참여자입니다.");
    }
}

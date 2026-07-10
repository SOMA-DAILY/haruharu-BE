package org.son.haruharu.admin.domain.room.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class AdminRoomNotFoundException extends NotFoundException {
    public AdminRoomNotFoundException() {
        super("ADMIN_ROOM_NOT_FOUND", "존재하지 않는 방입니다.");
    }
}

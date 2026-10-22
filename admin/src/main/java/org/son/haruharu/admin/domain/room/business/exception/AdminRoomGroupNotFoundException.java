package org.son.haruharu.admin.domain.room.business.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class AdminRoomGroupNotFoundException extends NotFoundException {
    public AdminRoomGroupNotFoundException() {
        super("ADMIN_ROOM_GROUP_NOT_FOUND", "기수를 찾을 수 없습니다.");
    }
}

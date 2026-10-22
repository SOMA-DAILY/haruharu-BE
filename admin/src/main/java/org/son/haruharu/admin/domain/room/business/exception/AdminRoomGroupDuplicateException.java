package org.son.haruharu.admin.domain.room.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class AdminRoomGroupDuplicateException extends ConflictException {
    public AdminRoomGroupDuplicateException() {
        super("ADMIN_ROOM_GROUP_DUPLICATE", "이미 있는 기수 이름입니다.");
    }
}

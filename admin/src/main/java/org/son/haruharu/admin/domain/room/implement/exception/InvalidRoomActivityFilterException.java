package org.son.haruharu.admin.domain.room.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidRoomActivityFilterException extends BadRequestException {
    public InvalidRoomActivityFilterException() {
        super("ADMIN_ROOM_ACTIVITY_INVALID_FILTER", "일차, 참여자, 활동 유형 또는 페이지 조건을 확인해 주세요.");
    }
}

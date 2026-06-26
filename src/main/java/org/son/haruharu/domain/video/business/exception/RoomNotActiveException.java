package org.son.haruharu.domain.video.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class RoomNotActiveException extends ConflictException {

    public RoomNotActiveException() {
        super("VIDEO_ROOM_NOT_ACTIVE", "진행 중인 방이 아니어서 업로드할 수 없습니다.");
    }
}

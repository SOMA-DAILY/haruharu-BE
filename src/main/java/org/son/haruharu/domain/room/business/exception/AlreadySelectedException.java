package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class AlreadySelectedException extends ConflictException {

    public AlreadySelectedException() {
        super("ROOM_409_ALREADY_SELECTED", "이미 최종선택을 완료했습니다. 변경할 수 없습니다.");
    }
}

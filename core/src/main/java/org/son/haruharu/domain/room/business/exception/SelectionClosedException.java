package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class SelectionClosedException extends ConflictException {

    public SelectionClosedException() {
        super("ROOM_409_SELECTION_CLOSED", "최종선택이 마감되었습니다. 결과 발표(22:00) 이후에는 선택할 수 없습니다.");
    }
}

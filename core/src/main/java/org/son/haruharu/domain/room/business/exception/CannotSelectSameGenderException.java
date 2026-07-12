package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class CannotSelectSameGenderException extends BadRequestException {

    public CannotSelectSameGenderException() {
        super("ROOM_400_CANNOT_SELECT_SAME_GENDER", "같은 성별의 멤버는 최종선택할 수 없습니다.");
    }
}
